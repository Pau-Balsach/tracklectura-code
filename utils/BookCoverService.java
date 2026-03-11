package utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BookCoverService {
    public static BufferedImage fetchCoverImage(String title) {
        List<String> urls = fetchCoverUrls(title);
        if (urls != null && !urls.isEmpty()) {
            return downloadImage(urls.get(0));
        }
        return null;
    }

    public static List<String> fetchCoverUrls(String title) {
        if (title == null || title.trim().isEmpty())
            return new ArrayList<>();

        List<String> urls = fetchUrlsFromGoogle(title);
        if (urls.isEmpty()) {
            System.out.println("No cover found on Google Books. Fetching from OpenLibrary...");
            urls = fetchUrlsFromOpenLibrary(title);
        }
        return urls;
    }

    public static BufferedImage downloadImage(String urlStr) {
        try {
            if (urlStr == null || urlStr.isEmpty())
                return null;

            if (urlStr.startsWith("http")) {
                HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                return ImageIO.read(conn.getInputStream());
            } else {
                // Handle as local file path
                File file = new File(urlStr);
                if (file.exists()) {
                    return ImageIO.read(file);
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error downloading/loading image: " + e.getMessage());
            return null;
        }
    }

    private static List<String> fetchUrlsFromGoogle(String title) {
        List<String> urls = new ArrayList<>();
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String apiUrl = "https://www.googleapis.com/books/v1/volumes?q=intitle:" + encodedTitle + "&maxResults=5";

            String json = fetchStringFromUrl(apiUrl);
            if (json == null)
                return urls;

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("items")) {
                JsonArray items = root.getAsJsonArray("items");
                for (JsonElement item : items) {
                    JsonObject volumeInfo = item.getAsJsonObject().getAsJsonObject("volumeInfo");
                    if (volumeInfo.has("imageLinks")) {
                        JsonObject imageLinks = volumeInfo.getAsJsonObject("imageLinks");
                        String link = null;
                        if (imageLinks.has("thumbnail")) {
                            link = imageLinks.get("thumbnail").getAsString();
                        } else if (imageLinks.has("smallThumbnail")) {
                            link = imageLinks.get("smallThumbnail").getAsString();
                        }

                        if (link != null) {
                            String u = link.replace("http://", "https://").replace("\\u0026", "&");
                            if (!urls.contains(u))
                                urls.add(u);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Google fetch error: " + e.getMessage());
        }
        return urls;
    }

    private static List<String> fetchUrlsFromOpenLibrary(String title) {
        List<String> urls = new ArrayList<>();
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String apiUrl = "https://openlibrary.org/search.json?q=" + encodedTitle + "&limit=5";

            String json = fetchStringFromUrl(apiUrl);
            if (json == null)
                return urls;

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("docs")) {
                JsonArray docs = root.getAsJsonArray("docs");
                for (JsonElement doc : docs) {
                    JsonObject docObj = doc.getAsJsonObject();
                    if (docObj.has("cover_i")) {
                        String imgUrl = "https://covers.openlibrary.org/b/id/" + docObj.get("cover_i").getAsString()
                                + "-L.jpg";
                        if (!urls.contains(imgUrl))
                            urls.add(imgUrl);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("OpenLibrary fetch error: " + e.getMessage());
        }
        return urls;
    }

    private static String fetchStringFromUrl(String urlStr) {
        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ReadingTracker/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() != 200)
                return null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                    sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }
}