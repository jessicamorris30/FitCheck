package com.fitcheck.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app (from {@code classpath:/static/}, filled by the Docker build) at the
 * same address as the API. One address means the refresh cookie and CORS just work.
 *
 * <p>React Router handles pages like {@code /wardrobe/123} in the browser, but if you reload or
 * open such a link, the BROWSER asks the server for {@code /wardrobe/123}. There's no such file,
 * so we answer with {@code index.html} and let React show the right page ("SPA fallback").
 *
 * <p>Not for: {@code /api/...} (a missing API path must stay a JSON 404, never a web page) and
 * paths that look like files ({@code .js}, {@code .png}...): a missing file must be a real 404.
 *
 * <p>In local development there's no {@code static/index.html} (Vite serves the frontend on
 * :5173), so this simply finds nothing and the backend answers 404 for non-API paths.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite names built files after their content (index-CPyUeCm3.js), so a changed file gets a
        // new name. That makes it safe to let browsers cache them for a year.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        // Everything else: real files if they exist, else index.html for app pages. Never cached,
        // so a new deployment is picked up on the next page load.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        boolean isApi = path.startsWith("api/") || path.equals("api");
                        boolean looksLikeFile = path.substring(path.lastIndexOf('/') + 1).contains(".");
                        if (isApi || looksLikeFile) {
                            return null; // → a real 404
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
