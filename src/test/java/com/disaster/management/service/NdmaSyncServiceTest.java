package com.disaster.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.disaster.management.entity.OfficialAlert;
import com.disaster.management.repository.OfficialAlertRepository;

@ExtendWith(MockitoExtension.class)
public class NdmaSyncServiceTest {

    @Mock
    private OfficialAlertRepository officialAlertRepository;

    private NdmaSyncService ndmaSyncService;

    @BeforeEach
    void setUp() {
        ndmaSyncService = new NdmaSyncService(officialAlertRepository);
    }

    @Test
    void testParseCapXml_Success() {
        String sampleCapXml = """
            <cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
                <cap:identifier>IN-1793294058125010_10</cap:identifier>
                <cap:sender>Assam-SDMA</cap:sender>
                <cap:sent>2026-10-29T22:47:18+05:30</cap:sent>
                <cap:status>Actual</cap:status>
                <cap:msgType>Update</cap:msgType>
                <cap:scope>Public</cap:scope>
                <cap:info>
                    <cap:category>Met</cap:category>
                    <cap:event>Thunderstorm with Lightning</cap:event>
                    <cap:urgency>Expected</cap:urgency>
                    <cap:severity>Moderate</cap:severity>
                    <cap:certainty>Possible</cap:certainty>
                    <cap:effective>2026-10-29T22:42:00+05:30</cap:effective>
                    <cap:expires>2026-10-30T01:42:00+05:30</cap:expires>
                    <cap:headline>IMD Guwahati forecast for Thunderstorm with Lightning over Cachar</cap:headline>
                    <cap:instruction>Please follow SDMA guidelines.</cap:instruction>
                    <cap:area>
                        <cap:areaDesc>Cachar district of Assam</cap:areaDesc>
                    </cap:area>
                </cap:info>
            </cap:alert>
            """;

        NdmaSyncService.RssItem rssItem = new NdmaSyncService.RssItem(
            "1793294058125010",
            "Thunderstorm Alert",
            "https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=1793294058125010",
            "controlroom@ndma.gov.in (IMD Guwahati)",
            "Thu, 29 Oct 2026 17:17:19 GMT"
        );

        OfficialAlert alert = ndmaSyncService.parseCapXml(sampleCapXml, rssItem);

        assertNotNull(alert);
        assertEquals("1793294058125010", alert.getSourceAlertId());
        assertEquals("IN-1793294058125010_10", alert.getCapIdentifier());
        assertEquals("Assam-SDMA", alert.getSourceAgency());
        assertEquals("Thunderstorm with Lightning", alert.getHazardType());
        assertEquals("Moderate", alert.getSeverity());
        assertEquals("Expected", alert.getUrgency());
        assertEquals("Possible", alert.getCertainty());
        assertEquals("Cachar district of Assam", alert.getAreaDescription());
        assertEquals("DISTRICT_CENTROID", alert.getLocationPrecision());
        assertNotNull(alert.getLatitude());
        assertNotNull(alert.getLongitude());
        assertEquals(24.8333, alert.getLatitude(), 0.001);
        assertEquals(92.7789, alert.getLongitude(), 0.001);
    }

    @Test
    void testIndiaGeoGazetteer_CentroidMatching() {
        // District centroid test
        IndiaGeoGazetteer.GeoLocation locChennai = IndiaGeoGazetteer.resolveLocation("Heavy rainfall in Chennai district", "", "");
        assertEquals("DISTRICT_CENTROID", locChennai.precision());
        assertEquals(13.0827, locChennai.latitude(), 0.001);
        assertEquals(80.2707, locChennai.longitude(), 0.001);

        // State centroid test
        IndiaGeoGazetteer.GeoLocation locOdisha = IndiaGeoGazetteer.resolveLocation("Coastal alert for Odisha", "", "");
        assertEquals("STATE_CENTROID", locOdisha.precision());
        assertEquals(20.9517, locOdisha.latitude(), 0.001);
        assertEquals(85.0985, locOdisha.longitude(), 0.001);

        // Fallback national test
        IndiaGeoGazetteer.GeoLocation locIndia = IndiaGeoGazetteer.resolveLocation("Unspecified regional advisory", "", "");
        assertEquals("NATIONAL_CENTROID", locIndia.precision());
        assertEquals(20.5937, locIndia.latitude(), 0.001);
    }

    @Test
    void testSafeXmlParsing_DisallowDoctypeXxe() {
        String xxePayload = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <!DOCTYPE foo [
            <!ELEMENT foo ANY >
            <!ENTITY xxe SYSTEM "file:///c:/boot.ini" >]>
            <cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
                <cap:identifier>&xxe;</cap:identifier>
            </cap:alert>
            """;

        NdmaSyncService.RssItem rssItem = new NdmaSyncService.RssItem(
            "XXE_TEST", "XXE Test", "http://test", "tester", "Thu, 29 Oct 2026 17:17:19 GMT"
        );

        // Safe XML parser must catch/reject DOCTYPE and fall back safely without executing entity
        OfficialAlert alert = ndmaSyncService.parseCapXml(xxePayload, rssItem);
        assertNotNull(alert);
        // Alert should fall back safely to fallback metadata rather than parsing malicious XML
        assertEquals("XXE_TEST", alert.getSourceAlertId());
        assertFalse(alert.getHeadline().contains("boot.ini"));
    }
}

