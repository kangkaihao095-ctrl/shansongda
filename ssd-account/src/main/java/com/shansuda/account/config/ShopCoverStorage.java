package com.shansuda.account.config;

import com.shansuda.common.api.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 店铺头图落到 data/shop-covers/，对外 /api/shop-covers/。 */
@Component
public class ShopCoverStorage {

    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, String> EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    @Value("${ssd.shop-covers.dir:data/shop-covers}")
    private String dir;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Path root() {
        return root;
    }

    public String save(long merchantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("BAD_COVER", "请选择店铺头图");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.badRequest("BAD_COVER", "头图不能超过 2MB");
        }
        String contentType = normalizeType(file);
        if (!ALLOWED.contains(contentType)) {
            throw BizException.badRequest("BAD_COVER", "仅支持 jpg / png / webp / gif");
        }
        String ext = EXT.get(contentType);
        String filename = merchantId + "." + ext;
        Path target = root.resolve(filename);
        try {
            try (var stream = Files.list(root)) {
                stream.filter(p -> p.getFileName().toString().startsWith(merchantId + "."))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // 旧文件删不掉不影响本次写入
                            }
                        });
            }
            Files.copy(file.getInputStream(), target);
        } catch (IOException ex) {
            throw BizException.badRequest("BAD_COVER", "头图保存失败");
        }
        return "/api/shop-covers/" + filename + "?v=" + System.currentTimeMillis();
    }

    public boolean acceptedUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String path = url.trim();
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        if (path.contains("..")) {
            return false;
        }
        return path.startsWith("/api/shop-covers/")
                || path.startsWith("/api/avatars/")
                || path.startsWith("/images/");
    }

    private static String normalizeType(MultipartFile file) {
        String type = file.getContentType();
        if (type == null || type.isBlank()) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (name.endsWith(".png")) {
                return "image/png";
            }
            if (name.endsWith(".webp")) {
                return "image/webp";
            }
            if (name.endsWith(".gif")) {
                return "image/gif";
            }
            return "image/jpeg";
        }
        if ("image/jpg".equals(type)) {
            return "image/jpeg";
        }
        return type;
    }
}
