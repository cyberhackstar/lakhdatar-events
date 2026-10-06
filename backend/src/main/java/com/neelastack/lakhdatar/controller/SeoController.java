package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.service.PublicEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * robots.txt and sitemap.xml. URLs are built from the configured canonical public origin only,
 * never from the request Host header, so a spoofed Host can never poison search-engine output.
 */
@RestController
@RequiredArgsConstructor
public class SeoController {

    private final PublicEventService events;

    @Value("${app.public-base-url:https://events.neelastack.com}")
    private String publicBaseUrl;

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String base = base();
        String body = "User-agent: *\n"
                + "Allow: /\n"
                + "Disallow: /admin\n"
                + "Disallow: /staff\n"
                + "Disallow: /checkout\n"
                + "Disallow: /payment/\n"
                + "Disallow: /ticket/\n"
                + "Disallow: /recover\n"
                + "Disallow: /login\n"
                + "Disallow: /setup/\n"
                + "Disallow: /monitor\n"
                + "Disallow: /api/\n\n"
                + "Sitemap: " + base + "/sitemap.xml\n";
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(body);
    }

    private static final int SITEMAP_PAGE_SIZE = 10_000;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> sitemapIndex() {
        String base = base();
        long total = events.publishedEventCount();
        long pages = Math.max(1L, (total + SITEMAP_PAGE_SIZE - 1L) / SITEMAP_PAGE_SIZE);
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (long page = 1; page <= pages; page++) {
            x.append("  <sitemap><loc>")
                    .append(escape(base + "/sitemap-" + page + ".xml"))
                    .append("</loc></sitemap>\n");
        }
        x.append("</sitemapindex>\n");
        return xml(x);
    }

    @GetMapping(value = "/sitemap-{page:[0-9]+}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> sitemapPage(@org.springframework.web.bind.annotation.PathVariable int page) {
        if (page < 1) {
            return ResponseEntity.notFound().build();
        }
        long total = events.publishedEventCount();
        long totalPages = Math.max(1L, (total + SITEMAP_PAGE_SIZE - 1L) / SITEMAP_PAGE_SIZE);
        if ((long) page > totalPages) {
            return ResponseEntity.notFound().build();
        }
        String base = base();
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        if (page == 1) {
            entry(x, base + "/", null, "daily", "1.0");
            entry(x, base + "/events", null, "daily", "0.9");
        }
        for (PublicEventService.SitemapEntry e : events.sitemapPage(page - 1, SITEMAP_PAGE_SIZE)) {
            entry(x, base + "/events/" + e.slug(), e.lastModified(), "daily", "0.8");
        }
        x.append("</urlset>\n");
        return xml(x);
    }

    private ResponseEntity<byte[]> xml(StringBuilder body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String base() {
        return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    private static void entry(StringBuilder x, String loc, Instant lastMod, String freq, String prio) {
        x.append("  <url><loc>").append(escape(loc)).append("</loc>");
        if (lastMod != null) x.append("<lastmod>").append(lastMod.truncatedTo(ChronoUnit.SECONDS)).append("</lastmod>");
        x.append("<changefreq>").append(freq).append("</changefreq><priority>").append(prio).append("</priority></url>\n");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
