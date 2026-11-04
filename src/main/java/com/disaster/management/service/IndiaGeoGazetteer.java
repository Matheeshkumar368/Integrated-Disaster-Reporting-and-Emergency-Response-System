package com.disaster.management.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Indian Geographic Gazetteer
 * Provides centroid coordinates and precision classifications for Indian states and districts.
 * Prioritizes actual affected area descriptions (<areaDesc>) before headline/sender metadata.
 */
public final class IndiaGeoGazetteer {

    public record GeoLocation(double latitude, double longitude, String precision, String matchedName) {}

    private static final Map<String, double[]> DISTRICT_CENTROIDS = new LinkedHashMap<>();
    private static final Map<String, double[]> STATE_CENTROIDS = new LinkedHashMap<>();

    static {
        // --- TAMIL NADU DISTRICTS ---
        DISTRICT_CENTROIDS.put("chennai", new double[]{13.0827, 80.2707});
        DISTRICT_CENTROIDS.put("coimbatore", new double[]{11.0168, 76.9558});
        DISTRICT_CENTROIDS.put("madurai", new double[]{9.9252, 78.1198});
        DISTRICT_CENTROIDS.put("tiruchirappalli", new double[]{10.7905, 78.7047});
        DISTRICT_CENTROIDS.put("trichy", new double[]{10.7905, 78.7047});
        DISTRICT_CENTROIDS.put("salem", new double[]{11.6643, 78.1460});
        DISTRICT_CENTROIDS.put("tirunelveli", new double[]{8.7139, 77.7567});
        DISTRICT_CENTROIDS.put("kanyakumari", new double[]{8.0883, 77.5385});
        DISTRICT_CENTROIDS.put("ooty", new double[]{11.4102, 76.6950});
        DISTRICT_CENTROIDS.put("nilgiris", new double[]{11.4102, 76.6950});
        DISTRICT_CENTROIDS.put("the nilgiris", new double[]{11.4102, 76.6950});
        DISTRICT_CENTROIDS.put("vellore", new double[]{12.9165, 79.1325});
        DISTRICT_CENTROIDS.put("dindigul", new double[]{10.3673, 77.9803});
        DISTRICT_CENTROIDS.put("cuddalore", new double[]{11.7480, 79.7714});
        DISTRICT_CENTROIDS.put("kancheepuram", new double[]{12.8342, 79.7036});
        DISTRICT_CENTROIDS.put("kanchipuram", new double[]{12.8342, 79.7036});
        DISTRICT_CENTROIDS.put("thiruvallur", new double[]{13.1433, 79.9079});
        DISTRICT_CENTROIDS.put("tiruvallur", new double[]{13.1433, 79.9079});
        DISTRICT_CENTROIDS.put("nagapattinam", new double[]{10.7656, 79.8424});
        DISTRICT_CENTROIDS.put("thanjavur", new double[]{10.7870, 79.1378});
        DISTRICT_CENTROIDS.put("tiruvarur", new double[]{10.7725, 79.6365});
        DISTRICT_CENTROIDS.put("ramanathapuram", new double[]{9.3639, 78.8395});
        DISTRICT_CENTROIDS.put("thoothukudi", new double[]{8.7642, 78.1348});
        DISTRICT_CENTROIDS.put("tuticorin", new double[]{8.7642, 78.1348});
        DISTRICT_CENTROIDS.put("dharmapuri", new double[]{12.1277, 78.1579});
        DISTRICT_CENTROIDS.put("krishnagiri", new double[]{12.5186, 78.2137});
        DISTRICT_CENTROIDS.put("namakkal", new double[]{11.2189, 78.1674});
        DISTRICT_CENTROIDS.put("erode", new double[]{11.3410, 77.7172});
        DISTRICT_CENTROIDS.put("tiruppur", new double[]{11.1085, 77.3411});
        DISTRICT_CENTROIDS.put("karur", new double[]{10.9601, 78.0766});
        DISTRICT_CENTROIDS.put("perambalur", new double[]{11.2342, 78.8820});
        DISTRICT_CENTROIDS.put("ariyalur", new double[]{11.1401, 79.0786});
        DISTRICT_CENTROIDS.put("pudukkottai", new double[]{10.3797, 78.8208});
        DISTRICT_CENTROIDS.put("sivaganga", new double[]{9.8433, 78.4809});
        DISTRICT_CENTROIDS.put("virudhunagar", new double[]{9.5680, 77.9624});
        DISTRICT_CENTROIDS.put("theni", new double[]{10.0104, 77.4768});
        DISTRICT_CENTROIDS.put("tenkasi", new double[]{8.9594, 77.3150});
        DISTRICT_CENTROIDS.put("tirupathur", new double[]{12.4950, 78.5678});
        DISTRICT_CENTROIDS.put("ranipet", new double[]{12.9272, 79.3330});
        DISTRICT_CENTROIDS.put("chengalpattu", new double[]{12.6825, 79.9836});
        DISTRICT_CENTROIDS.put("kallakurichi", new double[]{11.7383, 78.9639});
        DISTRICT_CENTROIDS.put("mayiladuthurai", new double[]{11.1075, 79.6524});

        // --- ASSAM DISTRICTS ---
        DISTRICT_CENTROIDS.put("cachar", new double[]{24.8333, 92.7789});
        DISTRICT_CENTROIDS.put("kamrup", new double[]{26.3161, 91.5984});
        DISTRICT_CENTROIDS.put("kamrup metropolitan", new double[]{26.1445, 91.7362});
        DISTRICT_CENTROIDS.put("guwahati", new double[]{26.1445, 91.7362});
        DISTRICT_CENTROIDS.put("dibrugarh", new double[]{27.4728, 94.9120});
        DISTRICT_CENTROIDS.put("silchar", new double[]{24.8170, 92.7985});
        DISTRICT_CENTROIDS.put("jorhat", new double[]{26.7509, 94.2037});
        DISTRICT_CENTROIDS.put("nagaon", new double[]{26.3466, 92.6840});
        DISTRICT_CENTROIDS.put("sonitpur", new double[]{26.6528, 92.7926});
        DISTRICT_CENTROIDS.put("barpeta", new double[]{26.3211, 91.0066});
        DISTRICT_CENTROIDS.put("dhubri", new double[]{26.0207, 89.9742});
        DISTRICT_CENTROIDS.put("goalpara", new double[]{26.1770, 90.6258});
        DISTRICT_CENTROIDS.put("karbi anglong", new double[]{26.1522, 93.3667});
        DISTRICT_CENTROIDS.put("dima hasao", new double[]{25.1764, 93.0238});
        DISTRICT_CENTROIDS.put("hailakandi", new double[]{24.6837, 92.5647});
        DISTRICT_CENTROIDS.put("karimganj", new double[]{24.8690, 92.3592});
        DISTRICT_CENTROIDS.put("lakhimpur", new double[]{27.2366, 94.1037});
        DISTRICT_CENTROIDS.put("dhemaji", new double[]{27.4833, 94.5833});
        DISTRICT_CENTROIDS.put("tinsukia", new double[]{27.5000, 95.3667});
        DISTRICT_CENTROIDS.put("sivasagar", new double[]{26.9826, 94.6425});

        // --- ODISHA DISTRICTS ---
        DISTRICT_CENTROIDS.put("khordha", new double[]{20.1809, 85.6200});
        DISTRICT_CENTROIDS.put("bhubaneswar", new double[]{20.2961, 85.8245});
        DISTRICT_CENTROIDS.put("cuttack", new double[]{20.4625, 85.8828});
        DISTRICT_CENTROIDS.put("puri", new double[]{19.8135, 85.8312});
        DISTRICT_CENTROIDS.put("balasore", new double[]{21.4934, 86.9135});
        DISTRICT_CENTROIDS.put("bhadrak", new double[]{21.0574, 86.5160});
        DISTRICT_CENTROIDS.put("ganjam", new double[]{19.3800, 85.0600});
        DISTRICT_CENTROIDS.put("kendrapada", new double[]{20.5000, 86.4200});
        DISTRICT_CENTROIDS.put("jagatsinghpur", new double[]{20.2700, 86.1700});
        DISTRICT_CENTROIDS.put("mayurbhanj", new double[]{21.9300, 86.7300});
        DISTRICT_CENTROIDS.put("sambalpur", new double[]{21.4669, 83.9812});
        DISTRICT_CENTROIDS.put("sundargarh", new double[]{22.1200, 84.0300});
        DISTRICT_CENTROIDS.put("koraput", new double[]{18.8135, 82.7123});

        // --- KERALA DISTRICTS ---
        DISTRICT_CENTROIDS.put("thiruvananthapuram", new double[]{8.5241, 76.9366});
        DISTRICT_CENTROIDS.put("trivandrum", new double[]{8.5241, 76.9366});
        DISTRICT_CENTROIDS.put("ernakulam", new double[]{9.9816, 76.2999});
        DISTRICT_CENTROIDS.put("kochi", new double[]{9.9312, 76.2673});
        DISTRICT_CENTROIDS.put("kozhikode", new double[]{11.2588, 75.7804});
        DISTRICT_CENTROIDS.put("calicut", new double[]{11.2588, 75.7804});
        DISTRICT_CENTROIDS.put("wayanad", new double[]{11.6854, 76.1320});
        DISTRICT_CENTROIDS.put("idukki", new double[]{9.9189, 77.1025});
        DISTRICT_CENTROIDS.put("palakkad", new double[]{10.7867, 76.6548});
        DISTRICT_CENTROIDS.put("thrissur", new double[]{10.5276, 76.2144});
        DISTRICT_CENTROIDS.put("malappuram", new double[]{11.0510, 76.0711});
        DISTRICT_CENTROIDS.put("kollam", new double[]{8.8932, 76.6141});
        DISTRICT_CENTROIDS.put("alappuzha", new double[]{9.4981, 76.3388});
        DISTRICT_CENTROIDS.put("kottayam", new double[]{9.5916, 76.5222});
        DISTRICT_CENTROIDS.put("pathanamthitta", new double[]{9.2648, 76.7870});
        DISTRICT_CENTROIDS.put("kannur", new double[]{11.8745, 75.3704});
        DISTRICT_CENTROIDS.put("kasaragod", new double[]{12.5102, 74.9852});

        // --- ANDHRA PRADESH & TELANGANA ---
        DISTRICT_CENTROIDS.put("visakhapatnam", new double[]{17.6868, 83.2185});
        DISTRICT_CENTROIDS.put("vizag", new double[]{17.6868, 83.2185});
        DISTRICT_CENTROIDS.put("vijayawada", new double[]{16.5062, 80.6480});
        DISTRICT_CENTROIDS.put("guntur", new double[]{16.3067, 80.4365});
        DISTRICT_CENTROIDS.put("nellore", new double[]{14.4426, 79.9865});
        DISTRICT_CENTROIDS.put("tirupati", new double[]{13.6288, 79.4192});
        DISTRICT_CENTROIDS.put("kurnool", new double[]{15.8281, 78.0373});
        DISTRICT_CENTROIDS.put("hyderabad", new double[]{17.3850, 78.4867});
        DISTRICT_CENTROIDS.put("warangal", new double[]{17.9689, 79.5941});

        // --- WEST BENGAL DISTRICTS ---
        DISTRICT_CENTROIDS.put("kolkata", new double[]{22.5726, 88.3639});
        DISTRICT_CENTROIDS.put("howrah", new double[]{22.5958, 88.2636});
        DISTRICT_CENTROIDS.put("south 24 parganas", new double[]{22.1643, 88.4335});
        DISTRICT_CENTROIDS.put("north 24 parganas", new double[]{22.7230, 88.4807});
        DISTRICT_CENTROIDS.put("darjeeling", new double[]{27.0410, 88.2663});
        DISTRICT_CENTROIDS.put("siliguri", new double[]{26.7271, 88.3953});
        DISTRICT_CENTROIDS.put("jalpaiguri", new double[]{26.5414, 88.7196});
        DISTRICT_CENTROIDS.put("purba medinipur", new double[]{21.9333, 87.7667});
        DISTRICT_CENTROIDS.put("paschim medinipur", new double[]{22.4167, 87.3167});

        // --- MAHARASHTRA DISTRICTS ---
        DISTRICT_CENTROIDS.put("mumbai", new double[]{19.0760, 72.8777});
        DISTRICT_CENTROIDS.put("pune", new double[]{18.5204, 73.8567});
        DISTRICT_CENTROIDS.put("nagpur", new double[]{21.1458, 79.0882});
        DISTRICT_CENTROIDS.put("nashik", new double[]{19.9975, 73.7898});
        DISTRICT_CENTROIDS.put("thane", new double[]{19.2183, 72.9781});
        DISTRICT_CENTROIDS.put("raigad", new double[]{18.5158, 73.1822});
        DISTRICT_CENTROIDS.put("ratnagiri", new double[]{16.9902, 73.3120});
        DISTRICT_CENTROIDS.put("sindhudurg", new double[]{16.1194, 73.7042});
        DISTRICT_CENTROIDS.put("aurangabad", new double[]{19.8762, 75.3433});
        DISTRICT_CENTROIDS.put("chhatrapati sambhajinagar", new double[]{19.8762, 75.3433});
        DISTRICT_CENTROIDS.put("kolhapur", new double[]{16.7050, 74.2433});
        DISTRICT_CENTROIDS.put("solapur", new double[]{17.6599, 75.9064});

        // --- GUJARAT DISTRICTS ---
        DISTRICT_CENTROIDS.put("ahmedabad", new double[]{23.0225, 72.5714});
        DISTRICT_CENTROIDS.put("surat", new double[]{21.1702, 72.8311});
        DISTRICT_CENTROIDS.put("vadodara", new double[]{22.3072, 73.1812});
        DISTRICT_CENTROIDS.put("rajkot", new double[]{22.3039, 70.8022});
        DISTRICT_CENTROIDS.put("kutch", new double[]{23.7337, 69.8597});
        DISTRICT_CENTROIDS.put("jamnagar", new double[]{22.4707, 70.0577});
        DISTRICT_CENTROIDS.put("bhavnagar", new double[]{21.7645, 72.1519});

        // --- KARNATAKA DISTRICTS ---
        DISTRICT_CENTROIDS.put("bengaluru", new double[]{12.9716, 77.5946});
        DISTRICT_CENTROIDS.put("bangalore", new double[]{12.9716, 77.5946});
        DISTRICT_CENTROIDS.put("mysuru", new double[]{12.2958, 76.6394});
        DISTRICT_CENTROIDS.put("mysore", new double[]{12.2958, 76.6394});
        DISTRICT_CENTROIDS.put("mangaluru", new double[]{12.9141, 74.8560});
        DISTRICT_CENTROIDS.put("mangalore", new double[]{12.9141, 74.8560});
        DISTRICT_CENTROIDS.put("belagavi", new double[]{15.8497, 74.4977});
        DISTRICT_CENTROIDS.put("hubballi", new double[]{15.3647, 75.1240});
        DISTRICT_CENTROIDS.put("dharwad", new double[]{15.4589, 75.0078});
        DISTRICT_CENTROIDS.put("udupi", new double[]{13.3409, 74.7421});
        DISTRICT_CENTROIDS.put("uttara kannada", new double[]{14.7953, 74.6869});

        // --- NORTH INDIA MAJOR CITIES / DISTRICTS ---
        DISTRICT_CENTROIDS.put("delhi", new double[]{28.6139, 77.2090});
        DISTRICT_CENTROIDS.put("new delhi", new double[]{28.6139, 77.2090});
        DISTRICT_CENTROIDS.put("lucknow", new double[]{26.8467, 80.9462});
        DISTRICT_CENTROIDS.put("kanpur", new double[]{26.4499, 80.3319});
        DISTRICT_CENTROIDS.put("varanasi", new double[]{25.3176, 82.9739});
        DISTRICT_CENTROIDS.put("prayagraj", new double[]{25.4358, 81.8463});
        DISTRICT_CENTROIDS.put("allahabad", new double[]{25.4358, 81.8463});
        DISTRICT_CENTROIDS.put("patna", new double[]{25.5941, 85.1376});
        DISTRICT_CENTROIDS.put("gaya", new double[]{24.7914, 85.0002});
        DISTRICT_CENTROIDS.put("ranchi", new double[]{23.3441, 85.3096});
        DISTRICT_CENTROIDS.put("jamshedpur", new double[]{22.8046, 86.2029});
        DISTRICT_CENTROIDS.put("jaipur", new double[]{26.9124, 75.7873});
        DISTRICT_CENTROIDS.put("jodhpur", new double[]{26.2389, 73.0243});
        DISTRICT_CENTROIDS.put("udaipur", new double[]{24.5854, 73.7125});
        DISTRICT_CENTROIDS.put("chandigarh", new double[]{30.7333, 76.7794});
        DISTRICT_CENTROIDS.put("amritsar", new double[]{31.6340, 74.8723});
        DISTRICT_CENTROIDS.put("ludhiana", new double[]{30.9010, 75.8573});
        DISTRICT_CENTROIDS.put("srinagar", new double[]{34.0837, 74.7973});
        DISTRICT_CENTROIDS.put("jammu", new double[]{32.7266, 74.8570});
        DISTRICT_CENTROIDS.put("shimla", new double[]{31.1048, 77.1734});
        DISTRICT_CENTROIDS.put("dehradun", new double[]{30.3165, 78.0322});
        DISTRICT_CENTROIDS.put("bhopal", new double[]{23.2599, 77.4126});
        DISTRICT_CENTROIDS.put("indore", new double[]{22.7196, 75.8577});
        DISTRICT_CENTROIDS.put("raipur", new double[]{21.2514, 81.6296});

        // --- NORTHEAST STATES / REGIONS ---
        DISTRICT_CENTROIDS.put("shillong", new double[]{25.5788, 91.8933});
        DISTRICT_CENTROIDS.put("agartala", new double[]{23.8315, 91.2868});
        DISTRICT_CENTROIDS.put("imphal", new double[]{24.8170, 93.9368});
        DISTRICT_CENTROIDS.put("aizawl", new double[]{23.7271, 92.7176});
        DISTRICT_CENTROIDS.put("kohima", new double[]{25.6701, 94.1077});
        DISTRICT_CENTROIDS.put("itanagar", new double[]{27.0844, 93.6053});
        DISTRICT_CENTROIDS.put("gangtok", new double[]{27.3389, 88.6065});

        // --- STATE CENTROIDS ---
        STATE_CENTROIDS.put("tamil nadu", new double[]{11.1271, 78.6569});
        STATE_CENTROIDS.put("kerala", new double[]{10.8505, 76.2711});
        STATE_CENTROIDS.put("karnataka", new double[]{15.3173, 75.7139});
        STATE_CENTROIDS.put("andhra pradesh", new double[]{15.9129, 79.7400});
        STATE_CENTROIDS.put("telangana", new double[]{18.1124, 79.0193});
        STATE_CENTROIDS.put("maharashtra", new double[]{19.7515, 75.7139});
        STATE_CENTROIDS.put("gujarat", new double[]{22.2587, 71.1924});
        STATE_CENTROIDS.put("odisha", new double[]{20.9517, 85.0985});
        STATE_CENTROIDS.put("orissa", new double[]{20.9517, 85.0985});
        STATE_CENTROIDS.put("west bengal", new double[]{22.9868, 87.8550});
        STATE_CENTROIDS.put("assam", new double[]{26.2006, 92.9376});
        STATE_CENTROIDS.put("meghalaya", new double[]{25.4670, 91.3662});
        STATE_CENTROIDS.put("tripura", new double[]{23.9408, 91.9882});
        STATE_CENTROIDS.put("manipur", new double[]{24.6637, 93.9063});
        STATE_CENTROIDS.put("mizoram", new double[]{23.1645, 92.9376});
        STATE_CENTROIDS.put("nagaland", new double[]{26.1584, 94.5624});
        STATE_CENTROIDS.put("arunachal pradesh", new double[]{28.2180, 94.7278});
        STATE_CENTROIDS.put("sikkim", new double[]{27.5330, 88.5122});
        STATE_CENTROIDS.put("bihar", new double[]{25.0961, 85.3131});
        STATE_CENTROIDS.put("jharkhand", new double[]{23.6102, 85.2799});
        STATE_CENTROIDS.put("uttar pradesh", new double[]{26.8467, 80.9462});
        STATE_CENTROIDS.put("madhya pradesh", new double[]{22.9734, 78.6569});
        STATE_CENTROIDS.put("rajasthan", new double[]{27.0238, 74.2179});
        STATE_CENTROIDS.put("punjab", new double[]{31.1471, 75.3412});
        STATE_CENTROIDS.put("haryana", new double[]{29.0588, 76.0856});
        STATE_CENTROIDS.put("himachal pradesh", new double[]{31.1048, 77.1734});
        STATE_CENTROIDS.put("uttarakhand", new double[]{30.0668, 79.0193});
        STATE_CENTROIDS.put("chhattisgarh", new double[]{21.2787, 81.8661});
        STATE_CENTROIDS.put("goa", new double[]{15.2993, 74.1240});
        STATE_CENTROIDS.put("delhi", new double[]{28.6139, 77.2090});
        STATE_CENTROIDS.put("jammu and kashmir", new double[]{33.7782, 76.5762});
        STATE_CENTROIDS.put("ladakh", new double[]{34.1526, 77.5771});
    }

    private IndiaGeoGazetteer() {}

    /**
     * Resolves geographical coordinates from area description text, headline, or sender.
     * Evaluates areaDesc first (where the actual disaster is happening), then falls back
     * to headline, then sender, then national centroid.
     */
    public static GeoLocation resolveLocation(String areaDesc, String headline, String sender) {
        String cleanArea = (areaDesc != null ? areaDesc : "").toLowerCase(Locale.ENGLISH);

        // 1. Check district in areaDesc FIRST
        if (!cleanArea.isEmpty()) {
            for (Map.Entry<String, double[]> entry : DISTRICT_CENTROIDS.entrySet()) {
                if (containsWord(cleanArea, entry.getKey())) {
                    return new GeoLocation(entry.getValue()[0], entry.getValue()[1], "DISTRICT_CENTROID", capitalize(entry.getKey()));
                }
            }
            // 2. Check state in areaDesc
            for (Map.Entry<String, double[]> entry : STATE_CENTROIDS.entrySet()) {
                if (containsWord(cleanArea, entry.getKey())) {
                    return new GeoLocation(entry.getValue()[0], entry.getValue()[1], "STATE_CENTROID", capitalize(entry.getKey()));
                }
            }
        }

        // 3. Check headline for district
        String cleanHeadline = (headline != null ? headline : "").toLowerCase(Locale.ENGLISH);
        if (!cleanHeadline.isEmpty()) {
            for (Map.Entry<String, double[]> entry : DISTRICT_CENTROIDS.entrySet()) {
                if (containsWord(cleanHeadline, entry.getKey())) {
                    return new GeoLocation(entry.getValue()[0], entry.getValue()[1], "DISTRICT_CENTROID", capitalize(entry.getKey()));
                }
            }
            for (Map.Entry<String, double[]> entry : STATE_CENTROIDS.entrySet()) {
                if (containsWord(cleanHeadline, entry.getKey())) {
                    return new GeoLocation(entry.getValue()[0], entry.getValue()[1], "STATE_CENTROID", capitalize(entry.getKey()));
                }
            }
        }

        // 4. Check sender
        String cleanSender = (sender != null ? sender : "").toLowerCase(Locale.ENGLISH);
        if (!cleanSender.isEmpty()) {
            for (Map.Entry<String, double[]> entry : STATE_CENTROIDS.entrySet()) {
                if (containsWord(cleanSender, entry.getKey())) {
                    return new GeoLocation(entry.getValue()[0], entry.getValue()[1], "STATE_CENTROID", capitalize(entry.getKey()));
                }
            }
        }

        // 5. Fallback: National Centroid of India
        return new GeoLocation(20.5937, 78.9629, "NATIONAL_CENTROID", "India");
    }

    /**
     * Calculates polygon centroid if polygon string is provided in CAP XML.
     * Format: "lat,lon lat,lon lat,lon ..."
     */
    public static GeoLocation parsePolygonCentroid(String polygonStr) {
        if (polygonStr == null || polygonStr.trim().isEmpty()) {
            return null;
        }
        try {
            String[] points = polygonStr.trim().split("\\s+");
            if (points.length == 0) return null;
            double sumLat = 0;
            double sumLon = 0;
            int count = 0;
            for (String p : points) {
                String[] latLon = p.split(",");
                if (latLon.length == 2) {
                    double lat = Double.parseDouble(latLon[0].trim());
                    double lon = Double.parseDouble(latLon[1].trim());
                    sumLat += lat;
                    sumLon += lon;
                    count++;
                }
            }
            if (count > 0) {
                return new GeoLocation(sumLat / count, sumLon / count, "EXACT_COORDINATES", "Polygon Centroid");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean containsWord(String text, String target) {
        if (text == null || target == null) return false;
        return text.matches(".*\\b" + java.util.regex.Pattern.quote(target) + "\\b.*");
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
