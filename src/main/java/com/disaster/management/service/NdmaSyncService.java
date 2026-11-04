package com.disaster.management.service;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.disaster.management.entity.OfficialAlert;
import com.disaster.management.repository.OfficialAlertRepository;

/**
 * NDMA SACHET Live Alert Synchronization Service
 * Ingests genuine Common Alerting Protocol (CAP 1.2) warnings from NDMA SACHET RSS feeds.
 * Provides safe XML parsing (XXE disabled), duplicate prevention, lifecycle expiry management,
 * and sync health monitoring.
 */
@Service
public class NdmaSyncService {

    private static final Logger log = LoggerFactory.getLogger(NdmaSyncService.class);

    private final OfficialAlertRepository officialAlertRepository;
    private final HttpClient httpClient;

    @Value("${app.ndma.rss-url:https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml}")
    private String rssFeedUrl;

    @Value("${app.ndma.sync.max-items:30}")
    private int maxItemsToFetch;

    @Value("${app.ndma.sync.enabled:true}")
    private boolean syncEnabled;

    // Concurrency control to prevent overlapping runs
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    // Sync Telemetry & Health Monitoring
    private final AtomicReference<LocalDateTime> lastSyncAttempt = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastSuccessfulSync = new AtomicReference<>();
    private final AtomicLong totalRecordsImported = new AtomicLong(0);
    private final AtomicLong totalDuplicatesPrevented = new AtomicLong(0);
    private final AtomicReference<String> lastSyncStatus = new AtomicReference<>("IDLE");
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>(null);

    public record SyncMetrics(
        LocalDateTime lastSyncAttempt,
        LocalDateTime lastSuccessfulSync,
        long totalRecordsImported,
        long totalDuplicatesPrevented,
        String status,
        String lastError,
        long activeOfficialAlertsCount
    ) {}

    public record RssItem(String guid, String title, String link, String author, String pubDate) {}

    public NdmaSyncService(OfficialAlertRepository officialAlertRepository) {
        this.officialAlertRepository = officialAlertRepository;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Scheduled synchronization running every 5 minutes (configurable).
     * Non-overlapping execution guarded by isSyncing.
     */
    @Scheduled(fixedRateString = "${app.ndma.sync.interval-ms:300000}", initialDelayString = "${app.ndma.sync.initial-delay-ms:5000}")
    public void scheduledSync() {
        if (!syncEnabled) {
            log.debug("NDMA synchronization is disabled via configuration.");
            return;
        }
        syncNow();
    }

    /**
     * Manual or programmatically triggered synchronization.
     */
    public SyncMetrics syncNow() {
        if (!isSyncing.compareAndSet(false, true)) {
            log.warn("NDMA sync already in progress. Skipping concurrent request.");
            return getMetrics();
        }

        lastSyncAttempt.set(LocalDateTime.now());
        lastSyncStatus.set("RUNNING");
        lastErrorMessage.set(null);

        int importedInRun = 0;
        int duplicatesInRun = 0;

        try {
            log.info("Initiating NDMA SACHET alert sync from: {}", rssFeedUrl);

            // 1. Fetch RSS Feed
            HttpRequest rssRequest = HttpRequest.newBuilder()
                    .uri(URI.create(rssFeedUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "DisasterManagementAlertSystem/2.0 (NDMA CAP Integration)")
                    .header("Accept", "application/xml, text/xml, */*")
                    .GET()
                    .build();

            HttpResponse<String> rssResponse = httpClient.send(rssRequest, HttpResponse.BodyHandlers.ofString());

            if (rssResponse.statusCode() != 200 || rssResponse.body() == null || rssResponse.body().trim().isEmpty()) {
                throw new IllegalStateException("Upstream NDMA RSS returned HTTP " + rssResponse.statusCode());
            }

            // 2. Parse RSS Items
            List<RssItem> rssItems = parseRssFeed(rssResponse.body());
            log.info("Discovered {} items in NDMA RSS feed. Ingesting up to {} entries...", rssItems.size(), maxItemsToFetch);

            int itemsToProcess = Math.min(rssItems.size(), maxItemsToFetch);

            for (int i = 0; i < itemsToProcess; i++) {
                RssItem item = rssItems.get(i);
                try {
                    // Check if already present and up to date
                    Optional<OfficialAlert> existingOpt = officialAlertRepository.findBySourceAlertId(item.guid());
                    if (existingOpt.isPresent()) {
                        duplicatesInRun++;
                        totalDuplicatesPrevented.incrementAndGet();
                        continue;
                    }

                    // 3. Fetch detailed CAP 1.2 XML
                    if (item.link() != null && item.link().startsWith("http")) {
                        OfficialAlert alert = fetchAndParseCapAlert(item);
                        if (alert != null) {
                            officialAlertRepository.save(alert);
                            importedInRun++;
                            totalRecordsImported.incrementAndGet();
                        }
                    }
                } catch (Exception itemEx) {
                    log.warn("Error ingesting NDMA alert item [{}]: {}", item.guid(), itemEx.getMessage());
                }
            }

            // 4. Update expired alerts
            int expiredCount = officialAlertRepository.markExpiredAlerts(LocalDateTime.now());
            if (expiredCount > 0) {
                log.info("Marked {} expired official alerts as inactive.", expiredCount);
            }

            lastSuccessfulSync.set(LocalDateTime.now());
            lastSyncStatus.set("SUCCESS");
            log.info("NDMA SACHET sync completed: {} imported, {} duplicates skipped.", importedInRun, duplicatesInRun);

        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("NDMA SACHET synchronization failed: {}", err);
            lastSyncStatus.set("ERROR");
            lastErrorMessage.set(err);
        } finally {
            isSyncing.set(false);
        }

        return getMetrics();
    }

    /**
     * Safely parses RSS XML without external entity processing (XXE safe).
     */
    private List<RssItem> parseRssFeed(String xmlContent) throws Exception {
        List<RssItem> items = new ArrayList<>();
        Document doc = createSafeDocument(xmlContent);

        NodeList itemNodes = doc.getElementsByTagName("item");
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element el = (Element) itemNodes.item(i);
            String title = getDirectChildText(el, "title");
            String link = getDirectChildText(el, "link");
            String author = getDirectChildText(el, "author");
            String pubDate = getDirectChildText(el, "pubDate");
            String guid = getDirectChildText(el, "guid");

            if (guid == null || guid.trim().isEmpty()) {
                if (link != null && link.contains("identifier=")) {
                    guid = link.substring(link.indexOf("identifier=") + 11);
                } else {
                    guid = "NDMA-" + Math.abs((title != null ? title : "").hashCode());
                }
            }

            items.add(new RssItem(guid.trim(), title, link, author, pubDate));
        }

        return items;
    }

    /**
     * Fetches detailed CAP 1.2 XML for an item and constructs the OfficialAlert entity.
     */
    private OfficialAlert fetchAndParseCapAlert(RssItem item) {
        try {
            HttpRequest capRequest = HttpRequest.newBuilder()
                    .uri(URI.create(item.link()))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "DisasterManagementAlertSystem/2.0 (NDMA CAP Integration)")
                    .header("Accept", "application/xml, text/xml, */*")
                    .GET()
                    .build();

            HttpResponse<String> capResponse = httpClient.send(capRequest, HttpResponse.BodyHandlers.ofString());
            if (capResponse.statusCode() != 200 || capResponse.body() == null || capResponse.body().trim().isEmpty()) {
                log.warn("Upstream CAP endpoint returned HTTP {} for {}", capResponse.statusCode(), item.link());
                return createFallbackAlert(item);
            }

            return parseCapXml(capResponse.body(), item);

        } catch (Exception e) {
            log.warn("Failed to fetch full CAP XML from {}: {}. Using RSS metadata.", item.link(), e.getMessage());
            return createFallbackAlert(item);
        }
    }

    /**
     * Parses standard CAP 1.2 XML with XXE protections.
     */
    public OfficialAlert parseCapXml(String capXml, RssItem rssFallback) {
        try {
            Document doc = createSafeDocument(capXml);

            OfficialAlert alert = new OfficialAlert();
            alert.setSourceAlertId(rssFallback.guid());
            alert.setSourceUrl(rssFallback.link());

            // Root alert level tags
            String capIdentifier = getTagValue(doc, "identifier");
            alert.setCapIdentifier(capIdentifier != null ? capIdentifier : rssFallback.guid());

            String sender = getTagValue(doc, "sender");
            alert.setSourceAgency(sender != null && !sender.isEmpty() ? sender : rssFallback.author());

            String status = getTagValue(doc, "status");
            alert.setSourceStatus(status != null ? status : "Actual");

            String msgType = getTagValue(doc, "msgType");
            alert.setMessageType(msgType != null ? msgType : "Alert");

            // Info block tags
            String event = getTagValue(doc, "event");
            alert.setHazardType(event != null && !event.isEmpty() ? event : "General Advisory");

            String urgency = getTagValue(doc, "urgency");
            alert.setUrgency(urgency != null ? urgency : "Expected");

            String severity = getTagValue(doc, "severity");
            alert.setSeverity(severity != null ? severity : "Moderate");

            String certainty = getTagValue(doc, "certainty");
            alert.setCertainty(certainty != null ? certainty : "Observed");

            String headline = getTagValue(doc, "headline");
            alert.setHeadline(headline != null && !headline.isEmpty() ? headline : rssFallback.title());

            String description = getTagValue(doc, "description");
            alert.setDescription(description != null && !description.isEmpty() ? description : alert.getHeadline());

            String instruction = getTagValue(doc, "instruction");
            alert.setInstruction(instruction != null ? instruction : "Follow official disaster management instructions.");

            // Area and coordinates
            String areaDesc = getTagValue(doc, "areaDesc");
            alert.setAreaDescription(areaDesc != null ? areaDesc : "India");

            String polygon = getTagValue(doc, "polygon");
            IndiaGeoGazetteer.GeoLocation location = null;
            if (polygon != null && !polygon.trim().isEmpty()) {
                location = IndiaGeoGazetteer.parsePolygonCentroid(polygon);
            }
            if (location == null) {
                location = IndiaGeoGazetteer.resolveLocation(alert.getAreaDescription(), alert.getHeadline(), alert.getSourceAgency());
            }

            alert.setLatitude(location.latitude());
            alert.setLongitude(location.longitude());
            alert.setLocationPrecision(location.precision());

            // Timestamps
            alert.setPublishedTime(parseRssDate(rssFallback.pubDate()));
            String effectiveStr = getTagValue(doc, "effective");
            alert.setEffectiveTime(parseIsoDate(effectiveStr, alert.getPublishedTime()));

            String onsetStr = getTagValue(doc, "onset");
            alert.setOnset(parseIsoDate(onsetStr, alert.getEffectiveTime()));

            String expiresStr = getTagValue(doc, "expires");
            LocalDateTime expiresTime = parseIsoDate(expiresStr, alert.getEffectiveTime() != null ? alert.getEffectiveTime().plusHours(6) : LocalDateTime.now().plusHours(6));
            alert.setExpiresTime(expiresTime);

            alert.setIsExpired(expiresTime != null && expiresTime.isBefore(LocalDateTime.now()));
            alert.setSyncTimestamp(LocalDateTime.now());

            return alert;
        } catch (Exception e) {
            log.error("XML parse error on CAP payload: {}", e.getMessage());
            return createFallbackAlert(rssFallback);
        }
    }

    private OfficialAlert createFallbackAlert(RssItem item) {
        OfficialAlert alert = new OfficialAlert();
        alert.setSourceAlertId(item.guid());
        alert.setCapIdentifier(item.guid());
        alert.setSourceAgency(item.author() != null ? item.author() : "NDMA SACHET");
        alert.setSourceUrl(item.link());
        alert.setHeadline(item.title());
        alert.setDescription(item.title());
        alert.setHazardType("Disaster Advisory");
        alert.setSeverity("Moderate");
        alert.setUrgency("Expected");
        alert.setCertainty("Observed");
        alert.setAreaDescription("India");

        IndiaGeoGazetteer.GeoLocation loc = IndiaGeoGazetteer.resolveLocation(item.title(), item.title(), item.author());
        alert.setLatitude(loc.latitude());
        alert.setLongitude(loc.longitude());
        alert.setLocationPrecision(loc.precision());

        alert.setPublishedTime(parseRssDate(item.pubDate()));
        alert.setEffectiveTime(alert.getPublishedTime());
        alert.setExpiresTime(alert.getPublishedTime() != null ? alert.getPublishedTime().plusHours(12) : LocalDateTime.now().plusHours(12));
        alert.setIsExpired(alert.getExpiresTime().isBefore(LocalDateTime.now()));
        alert.setSyncTimestamp(LocalDateTime.now());
        alert.setSourceStatus("Actual");
        alert.setMessageType("Alert");
        return alert;
    }

    /**
     * Creates a hardened DocumentBuilderFactory with all XXE vectors disabled.
     */
    private Document createSafeDocument(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        DocumentBuilder builder = dbf.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private String getTagValue(Document doc, String localName) {
        // Search by localName or tagName
        NodeList nl = doc.getElementsByTagNameNS("*", localName);
        if (nl == null || nl.getLength() == 0) {
            nl = doc.getElementsByTagName("cap:" + localName);
        }
        if (nl == null || nl.getLength() == 0) {
            nl = doc.getElementsByTagName(localName);
        }
        if (nl != null && nl.getLength() > 0) {
            Node node = nl.item(0);
            return node.getTextContent() != null ? node.getTextContent().trim() : "";
        }
        return null;
    }

    private String getDirectChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = node.getNodeName();
                if (nodeName.equals(tagName) || nodeName.endsWith(":" + tagName)) {
                    return node.getTextContent() != null ? node.getTextContent().trim() : "";
                }
            }
        }
        return null;
    }

    private LocalDateTime parseIsoDate(String dateStr, LocalDateTime fallback) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return fallback != null ? fallback : LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(dateStr.trim()).toLocalDateTime();
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(dateStr.trim());
            } catch (Exception e2) {
                return fallback != null ? fallback : LocalDateTime.now();
            }
        }
    }

    private LocalDateTime parseRssDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateStr.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public SyncMetrics getMetrics() {
        long activeCount = 0;
        long totalInDb = 0;
        try {
            activeCount = officialAlertRepository.countByIsExpiredFalse();
            totalInDb = officialAlertRepository.count();
        } catch (Exception ignored) {}

        long imported = Math.max(totalRecordsImported.get(), totalInDb);

        return new SyncMetrics(
            lastSyncAttempt.get(),
            lastSuccessfulSync.get(),
            imported,
            totalDuplicatesPrevented.get(),
            lastSyncStatus.get(),
            lastErrorMessage.get(),
            activeCount
        );
    }
}

