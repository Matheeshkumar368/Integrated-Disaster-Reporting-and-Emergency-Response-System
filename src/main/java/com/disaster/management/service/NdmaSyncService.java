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
import org.springframework.beans.factory.annotation.Autowired;
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
 * Ingests genuine Common Alerting Protocol (CAP 1.2) warnings from official NDMA SACHET RSS/XML feeds.
 * Supports official ETag client-side caching (HTTP 304 / 200), duplicate prevention,
 * revision tracking, multilingual content, geotargeted state/district extraction, and role notifications.
 */
@Service
public class NdmaSyncService {

    private static final Logger log = LoggerFactory.getLogger(NdmaSyncService.class);

    private final OfficialAlertRepository officialAlertRepository;
    private final HttpClient httpClient;

    @Autowired(required = false)
    private NotificationService notificationService;

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
    private final AtomicReference<String> lastRssEtag = new AtomicReference<>(null);
    private final AtomicLong lastDurationMs = new AtomicLong(0);

    public record SyncMetrics(
        LocalDateTime lastSyncAttempt,
        LocalDateTime lastSuccessfulSync,
        long totalRecordsImported,
        long totalDuplicatesPrevented,
        String status, // "SUCCESS", "SUCCESS_304_CACHED", "RUNNING", "ERROR", "IDLE"
        String lastError,
        long activeOfficialAlertsCount,
        String lastEtag,
        long lastDurationMs,
        String activeFeedUrl
    ) {}

    public record RssItem(String guid, String title, String link, String author, String pubDate) {}

    public NdmaSyncService(OfficialAlertRepository officialAlertRepository) {
        this.officialAlertRepository = officialAlertRepository;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    /**
     * Scheduled synchronization running every 5 minutes (configurable via app.ndma.sync.interval-ms).
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
     * Manual or programmatically triggered synchronization with ETag caching.
     */
    public SyncMetrics syncNow() {
        if (!isSyncing.compareAndSet(false, true)) {
            log.warn("NDMA sync already in progress. Skipping concurrent request.");
            return getMetrics();
        }

        long startTime = System.currentTimeMillis();
        lastSyncAttempt.set(LocalDateTime.now());
        lastSyncStatus.set("RUNNING");
        lastErrorMessage.set(null);

        int importedInRun = 0;
        int duplicatesInRun = 0;

        try {
            log.info("Initiating NDMA SACHET alert sync from: {}", rssFeedUrl);

            // 1. Fetch RSS Feed with ETag If-None-Match header
            HttpRequest.Builder rssReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(rssFeedUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "DisasterManagementAlertSystem/2.0 (NDMA CAP Integration)")
                    .header("Accept", "application/xml, text/xml, */*")
                    .GET();

            String currentEtag = lastRssEtag.get();
            if (currentEtag != null && !currentEtag.trim().isEmpty()) {
                rssReqBuilder.header("If-None-Match", currentEtag);
            }

            HttpResponse<String> rssResponse = httpClient.send(rssReqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            // 2. Handle HTTP 304 Not Modified
            if (rssResponse.statusCode() == 304) {
                log.info("NDMA RSS Feed returned HTTP 304 (Not Modified). Reusing cached alerts; ETag is unchanged: {}", currentEtag);
                lastSuccessfulSync.set(LocalDateTime.now());
                lastSyncStatus.set("SUCCESS_304_CACHED");
                lastDurationMs.set(System.currentTimeMillis() - startTime);

                // Perform expiry management on existing active alerts
                officialAlertRepository.markExpiredAlerts(LocalDateTime.now());
                backfillMissingMetadata();
                return getMetrics();
            }

            if (rssResponse.statusCode() != 200 || rssResponse.body() == null || rssResponse.body().trim().isEmpty()) {
                throw new IllegalStateException("Upstream NDMA RSS returned HTTP " + rssResponse.statusCode());
            }

            // Update cached RSS ETag
            rssResponse.headers().firstValue("ETag").ifPresent(lastRssEtag::set);

            // 3. Parse RSS Items
            List<RssItem> rssItems = parseRssFeed(rssResponse.body());
            log.info("Discovered {} items in NDMA RSS feed. Ingesting up to {} entries...", rssItems.size(), maxItemsToFetch);

            int itemsToProcess = Math.min(rssItems.size(), maxItemsToFetch);

            for (int i = 0; i < itemsToProcess; i++) {
                RssItem item = rssItems.get(i);
                try {
                    Optional<OfficialAlert> existingOpt = officialAlertRepository.findFirstBySourceAlertIdOrderByEffectiveTimeDesc(item.guid());

                    // 4. Fetch detailed CAP 1.2 XML with per-alert ETag
                    if (item.link() != null && item.link().startsWith("http")) {
                        OfficialAlert alert = fetchAndParseCapAlert(item, existingOpt);
                        if (alert != null) {
                            if (existingOpt.isEmpty()) {
                                officialAlertRepository.save(alert);
                                importedInRun++;
                                totalRecordsImported.incrementAndGet();
                                dispatchAlertNotifications(alert);
                            } else {
                                duplicatesInRun++;
                                totalDuplicatesPrevented.incrementAndGet();
                            }
                        }
                    } else if (existingOpt.isEmpty()) {
                        OfficialAlert fallback = createFallbackAlert(item);
                        officialAlertRepository.save(fallback);
                        importedInRun++;
                        totalRecordsImported.incrementAndGet();
                        dispatchAlertNotifications(fallback);
                    } else {
                        duplicatesInRun++;
                        totalDuplicatesPrevented.incrementAndGet();
                    }
                } catch (Exception itemEx) {
                    log.warn("Error ingesting NDMA alert item [{}]: {}", item.guid(), itemEx.getMessage());
                }
            }

            // 5. Update expired alerts
            int expiredCount = officialAlertRepository.markExpiredAlerts(LocalDateTime.now());
            if (expiredCount > 0) {
                log.info("Marked {} expired official alerts as inactive.", expiredCount);
            }

            lastSuccessfulSync.set(LocalDateTime.now());
            lastSyncStatus.set("SUCCESS");
            lastDurationMs.set(System.currentTimeMillis() - startTime);
            log.info("NDMA SACHET sync completed in {}ms: {} imported, {} duplicates skipped.", lastDurationMs.get(), importedInRun, duplicatesInRun);

        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("NDMA SACHET synchronization failed: {}", err);
            lastSyncStatus.set("ERROR");
            lastErrorMessage.set(err);
            lastDurationMs.set(System.currentTimeMillis() - startTime);
        } finally {
            isSyncing.set(false);
        }

        return getMetrics();
    }

    public void backfillMissingMetadata() {
        try {
            List<OfficialAlert> list = officialAlertRepository.findAll();
            for (OfficialAlert alert : list) {
                boolean changed = false;
                if (alert.getState() == null || alert.getState().trim().isEmpty() || "National / All India".equals(alert.getState())) {
                    String state = detectState(alert.getAreaDescription(), alert.getSourceAgency());
                    if (state == null || "National / All India".equals(state)) {
                        String s2 = detectState(alert.getHeadline(), alert.getSourceAgency());
                        if (s2 != null && !"National / All India".equals(s2)) state = s2;
                    }
                    if (state != null && !state.equals(alert.getState())) {
                        alert.setState(state);
                        changed = true;
                    }
                }
                if (alert.getDistrict() == null || alert.getDistrict().trim().isEmpty()) {
                    String dist = detectDistrict(alert.getAreaDescription());
                    if (dist == null) dist = detectDistrict(alert.getHeadline());
                    if (dist != null && !dist.equals(alert.getDistrict())) {
                        alert.setDistrict(dist);
                        changed = true;
                    }
                }
                if (changed) {
                    officialAlertRepository.save(alert);
                }
            }
        } catch (Exception e) {
            log.warn("Backfill warning: {}", e.getMessage());
        }
    }

    /**
     * Safely parses RSS XML without external entity processing (XXE safe).
     */
    public List<RssItem> parseRssFeed(String xmlContent) throws Exception {
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
     * Fetches detailed CAP 1.2 XML for an item and constructs/updates the OfficialAlert entity.
     * Follows official ETag conditional request specifications (HTTP 304 / 200).
     */
    private OfficialAlert fetchAndParseCapAlert(RssItem item, Optional<OfficialAlert> existingOpt) {
        try {
            HttpRequest.Builder capReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(item.link()))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "DisasterManagementAlertSystem/2.0 (NDMA CAP Integration)")
                    .header("Accept", "application/xml, text/xml, */*")
                    .GET();

            if (existingOpt.isPresent() && existingOpt.get().getEtag() != null) {
                capReqBuilder.header("If-None-Match", existingOpt.get().getEtag());
            }

            HttpResponse<String> capResponse = httpClient.send(capReqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            // 304 Not Modified: Reuse existing cached alert and touch sync timestamp
            if (capResponse.statusCode() == 304 && existingOpt.isPresent()) {
                OfficialAlert existing = existingOpt.get();
                existing.setSyncTimestamp(LocalDateTime.now());
                return officialAlertRepository.save(existing);
            }

            if (capResponse.statusCode() != 200 || capResponse.body() == null || capResponse.body().trim().isEmpty()) {
                log.warn("Upstream CAP endpoint returned HTTP {} for {}", capResponse.statusCode(), item.link());
                return existingOpt.orElseGet(() -> createFallbackAlert(item));
            }

            String capEtag = capResponse.headers().firstValue("ETag").orElse(null);
            return parseCapXml(capResponse.body(), item, existingOpt, capEtag);

        } catch (Exception e) {
            log.warn("Failed to fetch full CAP XML from {}: {}. Using fallback/cached.", item.link(), e.getMessage());
            return existingOpt.orElseGet(() -> createFallbackAlert(item));
        }
    }

    /**
     * Overloaded helper for backward compatibility with 2-argument callers/tests.
     */
    public OfficialAlert parseCapXml(String capXml, RssItem rssFallback) {
        return parseCapXml(capXml, rssFallback, Optional.empty(), null);
    }

    /**
     * Parses standard CAP 1.2 XML with XXE protections and geotargeting.
     */
    public OfficialAlert parseCapXml(String capXml, RssItem rssFallback, Optional<OfficialAlert> existingOpt, String capEtag) {
        try {
            Document doc = createSafeDocument(capXml);

            OfficialAlert alert = existingOpt.orElseGet(OfficialAlert::new);
            alert.setSourceAlertId(rssFallback.guid());
            alert.setSourceUrl(rssFallback.link());
            if (capEtag != null) {
                alert.setEtag(capEtag);
            }

            // Root alert level tags
            String capIdentifier = getTagValue(doc, "identifier");
            alert.setCapIdentifier(capIdentifier != null && !capIdentifier.isEmpty() ? capIdentifier : rssFallback.guid());

            String sender = getTagValue(doc, "sender");
            alert.setSourceAgency(sender != null && !sender.isEmpty() ? sender : rssFallback.author());

            String status = getTagValue(doc, "status");
            alert.setSourceStatus(status != null && !status.isEmpty() ? status : "Actual");

            String msgType = getTagValue(doc, "msgType");
            alert.setMessageType(msgType != null && !msgType.isEmpty() ? msgType : "Alert");

            // Language
            String lang = getTagValue(doc, "language");
            alert.setLanguage(lang != null && !lang.isEmpty() ? lang : "en-IN");

            // Info block tags
            String event = getTagValue(doc, "event");
            alert.setHazardType(event != null && !event.isEmpty() ? event : "General Advisory");

            String urgency = getTagValue(doc, "urgency");
            alert.setUrgency(urgency != null && !urgency.isEmpty() ? urgency : "Expected");

            String severity = getTagValue(doc, "severity");
            alert.setSeverity(severity != null && !severity.isEmpty() ? severity : "Moderate");

            String certainty = getTagValue(doc, "certainty");
            alert.setCertainty(certainty != null && !certainty.isEmpty() ? certainty : "Observed");

            String headline = getTagValue(doc, "headline");
            alert.setHeadline(headline != null && !headline.isEmpty() ? headline : rssFallback.title());

            String description = getTagValue(doc, "description");
            alert.setDescription(description != null && !description.isEmpty() ? description : alert.getHeadline());

            String instruction = getTagValue(doc, "instruction");
            alert.setInstruction(instruction != null && !instruction.isEmpty() ? instruction : "Follow official disaster management instructions.");

            // Area and coordinates
            String areaDesc = getTagValue(doc, "areaDesc");
            alert.setAreaDescription(areaDesc != null && !areaDesc.isEmpty() ? areaDesc : "India");

            // Geotargeting: State & District
            String state = detectState(alert.getAreaDescription() + " " + alert.getHeadline(), alert.getSourceAgency());
            alert.setState(state);
            String district = detectDistrict(alert.getAreaDescription() + " " + alert.getHeadline());
            alert.setDistrict(district);

            String polygon = getTagValue(doc, "polygon");
            if (polygon != null && !polygon.trim().isEmpty()) {
                alert.setPolygon(polygon.trim());
            }

            IndiaGeoGazetteer.GeoLocation location = null;
            if (polygon != null && !polygon.trim().isEmpty()) {
                location = IndiaGeoGazetteer.parsePolygonCentroid(polygon);
            }
            if (location == null) {
                location = IndiaGeoGazetteer.resolveLocation(alert.getAreaDescription(), alert.getHeadline(), alert.getSourceAgency());
            }

            if (location != null) {
                alert.setLatitude(location.latitude());
                alert.setLongitude(location.longitude());
                alert.setLocationPrecision(location.precision());
            } else {
                // If the alert has no usable location information, leave coordinates null (unlocated)
                alert.setLatitude(null);
                alert.setLongitude(null);
                alert.setLocationPrecision("UNLOCATED");
            }

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

            if (existingOpt.isPresent()) {
                Integer currentRev = existingOpt.get().getRevisionNumber();
                alert.setRevisionNumber(currentRev != null ? currentRev + 1 : 2);
                alert.setUpdatedAt(LocalDateTime.now());
            } else if (alert.getRevisionNumber() == null) {
                alert.setRevisionNumber(1);
            }

            return alert;
        } catch (Exception e) {
            log.error("XML parse error on CAP payload: {}", e.getMessage());
            return existingOpt.orElseGet(() -> createFallbackAlert(rssFallback));
        }
    }

    /**
     * Fallback parsing when direct CAP XML endpoint is unavailable.
     */
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

        String state = detectState(item.title(), item.author());
        alert.setState(state);
        alert.setDistrict(detectDistrict(item.title()));

        IndiaGeoGazetteer.GeoLocation loc = IndiaGeoGazetteer.resolveLocation(item.title(), item.title(), item.author());
        if (loc != null) {
            alert.setLatitude(loc.latitude());
            alert.setLongitude(loc.longitude());
            alert.setLocationPrecision(loc.precision());
        } else {
            alert.setLatitude(null);
            alert.setLongitude(null);
            alert.setLocationPrecision("UNLOCATED");
        }

        alert.setPublishedTime(parseRssDate(item.pubDate()));
        alert.setEffectiveTime(alert.getPublishedTime());
        alert.setExpiresTime(alert.getPublishedTime() != null ? alert.getPublishedTime().plusHours(12) : LocalDateTime.now().plusHours(12));
        alert.setIsExpired(alert.getExpiresTime().isBefore(LocalDateTime.now()));
        alert.setSyncTimestamp(LocalDateTime.now());
        alert.setSourceStatus("Actual");
        alert.setMessageType("Alert");
        alert.setLanguage("en-IN");
        return alert;
    }

    /**
     * Dispatches notifications to relevant roles when a new genuine official alert is ingested.
     */
    private void dispatchAlertNotifications(OfficialAlert alert) {
        if (notificationService == null || alert == null) return;

        try {
            String prio = ("Extreme".equalsIgnoreCase(alert.getSeverity()) || "Severe".equalsIgnoreCase(alert.getSeverity())) ? "High" : "Medium";

            // 1. Notify Admins
            notificationService.notifyAdmins(
                "Official NDMA Alert: " + alert.getHazardType(),
                "[" + (alert.getSourceAgency() != null ? alert.getSourceAgency() : "NDMA SACHET") + "] " + alert.getHeadline(),
                "ALERTS",
                prio,
                alert.getId(),
                "OFFICIAL_ALERT",
                "admin-dashboard.html"
            );

            // 2. If alert is in Tamil Nadu or mentions local operational districts, notify Citizens & Responders
            if (isTamilNaduOrLocal(alert)) {
                notificationService.notifyAllCitizens(
                    "Official Weather Warning: " + alert.getHazardType(),
                    alert.getHeadline() != null ? alert.getHeadline() : alert.getDescription(),
                    "ALERTS",
                    prio,
                    alert.getId(),
                    "OFFICIAL_ALERT",
                    "citizen-notifications.html"
                );
            }

            alert.setIsNotified(true);
        } catch (Exception e) {
            log.warn("Notification dispatch warning for official alert #{}: {}", alert.getId(), e.getMessage());
        }
    }

    public static boolean isTamilNaduOrLocal(OfficialAlert alert) {
        if (alert == null) return false;
        String combined = ((alert.getState() != null ? alert.getState() : "") + " " +
                           (alert.getAreaDescription() != null ? alert.getAreaDescription() : "") + " " +
                           (alert.getHeadline() != null ? alert.getHeadline() : "") + " " +
                           (alert.getSourceAgency() != null ? alert.getSourceAgency() : "")).toLowerCase();

        return combined.contains("tamil nadu") || combined.contains("tamilnadu") ||
               combined.contains("chennai") || combined.contains("karur") ||
               combined.contains("coimbatore") || combined.contains("madurai") ||
               combined.contains("salem") || combined.contains("tiruchirappalli") ||
               combined.contains("cuddalore") || combined.contains("dharmapuri") ||
               combined.contains("puducherry") || combined.contains("pondicherry");
    }

    public static String detectState(String text, String agency) {
        if (text == null) text = "";
        if (agency == null) agency = "";
        String combined = (text + " " + agency).toLowerCase();

        if (combined.contains("tamil nadu") || combined.contains("tamilnadu") || combined.contains("chennai") || combined.contains("karur") || combined.contains("madurai") || combined.contains("coimbatore") || combined.contains("puducherry") || combined.contains("pondicherry") || combined.contains("chengalpattu") || combined.contains("kancheepuram")) {
            return "Tamil Nadu";
        }
        if (combined.contains("kerala") || combined.contains("thiruvananthapuram") || combined.contains("kochi")) {
            return "Kerala";
        }
        if (combined.contains("andhra") || combined.contains("visakhapatnam") || combined.contains("vijayawada")) {
            return "Andhra Pradesh";
        }
        if (combined.contains("karnataka") || combined.contains("bengaluru") || combined.contains("bangalore")) {
            return "Karnataka";
        }
        if (combined.contains("telangana") || combined.contains("hyderabad")) {
            return "Telangana";
        }
        if (combined.contains("west bengal") || combined.contains("kolkata") || combined.contains("murshidabad") || combined.contains("malda") || combined.contains("birbhum")) {
            return "West Bengal";
        }
        if (combined.contains("odisha") || combined.contains("orissa") || combined.contains("bhubaneswar")) {
            return "Odisha";
        }
        if (combined.contains("assam") || combined.contains("guwahati") || combined.contains("cachar")) {
            return "Assam";
        }
        if (combined.contains("jharkhand") || combined.contains("ranchi") || combined.contains("gumla") || combined.contains("simdega")) {
            return "Jharkhand";
        }
        if (combined.contains("chhattisgarh") || combined.contains("raipur") || combined.contains("bilaspur")) {
            return "Chhattisgarh";
        }
        if (combined.contains("tripura") || combined.contains("agartala")) {
            return "Tripura";
        }
        if (combined.contains("bihar") || combined.contains("patna")) {
            return "Bihar";
        }
        if (combined.contains("maharashtra") || combined.contains("mumbai") || combined.contains("pune")) {
            return "Maharashtra";
        }
        if (combined.contains("delhi")) {
            return "Delhi";
        }
        return "National / All India";
    }

    public static String detectDistrict(String text) {
        if (text == null) return null;
        String[] knownDistricts = {
            "Karur", "Chennai", "Chengalpattu", "Cuddalore", "Dharmapuri", "Kancheepuram",
            "Madurai", "Coimbatore", "Salem", "Tiruchirappalli", "Thanjavur", "Tirunelveli",
            "Vellore", "Erode", "Tiruppur", "Dindigul", "Theni", "Virudhunagar", "Pudukkottai",
            "Nagapattinam", "Namakkal", "Perambalur", "Thiruvarur", "Tiruvannamalai", "Viluppuram",
            "Ranchi", "Gumla", "Simdega", "Cachar", "Kolkata", "Bhubaneswar", "Raipur", "Bilaspur"
        };
        for (String dist : knownDistricts) {
            if (text.toLowerCase().contains(dist.toLowerCase())) {
                return dist;
            }
        }
        return null;
    }

    /**
     * Allows ingestion of an XML payload directly (useful for local test fixtures, agency webhooks, or fallback testing).
     */
    @Transactional
    public OfficialAlert ingestXmlPayload(String capXml, String sourceId, String sourceUrl, String author) {
        String capId = null;
        try {
            Document doc = createSafeDocument(capXml);
            capId = getTagValue(doc, "identifier");
        } catch (Exception ignored) {}

        String effectiveSourceId = (capId != null && !capId.trim().isEmpty()) ? capId.trim() : sourceId;

        Optional<OfficialAlert> existingOpt = Optional.empty();
        if (capId != null && !capId.trim().isEmpty()) {
            existingOpt = officialAlertRepository.findFirstByCapIdentifierOrderByEffectiveTimeDesc(capId.trim());
            if (existingOpt.isEmpty()) {
                existingOpt = officialAlertRepository.findFirstBySourceAlertIdOrderByEffectiveTimeDesc(capId.trim());
            }
        }
        if (existingOpt.isEmpty() && sourceId != null && !sourceId.trim().isEmpty()) {
            existingOpt = officialAlertRepository.findFirstBySourceAlertIdOrderByEffectiveTimeDesc(sourceId.trim());
        }

        RssItem item = new RssItem(effectiveSourceId, "Official Disaster Warning", sourceUrl, author, LocalDateTime.now().toString());
        OfficialAlert parsed = parseCapXml(capXml, item, existingOpt, "FIXTURE-" + System.currentTimeMillis());

        if (existingOpt.isPresent()) {
            Integer r = existingOpt.get().getRevisionNumber();
            parsed.setRevisionNumber(r != null ? r + 1 : 2);
            parsed.setUpdatedAt(LocalDateTime.now());
        } else if (parsed.getRevisionNumber() == null) {
            parsed.setRevisionNumber(1);
        }

        OfficialAlert saved = officialAlertRepository.save(parsed);
        if (existingOpt.isEmpty()) {
            totalRecordsImported.incrementAndGet();
            dispatchAlertNotifications(saved);
        } else {
            totalDuplicatesPrevented.incrementAndGet();
        }
        return saved;
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
            activeCount,
            lastRssEtag.get(),
            lastDurationMs.get(),
            rssFeedUrl
        );
    }
}
