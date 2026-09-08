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
import java.util.UUID;

/** 评价配图落到本地目录，对外暴露相对路径。 */
@Component
public class ReviewPhotoStorage {

    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, String> EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    @Value("${ssd.review-photos.dir:data/review-photos}")
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

    public String save(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("BAD_PHOTO", "请选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.badRequest("BAD_PHOTO", "图片不能超过 2MB");
        }
        String contentType = normalizeType(file);
        if (!ALLOWED.contains(contentType)) {
            throw BizException.badRequest("BAD_PHOTO", "仅支持 jpg / png / webp / gif");
        }
        String ext = EXT.get(contentType);
        String filename = userId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "." + ext;
        Path target = root.resolve(filename);
        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException ex) {
            throw BizException.badRequest("BAD_PHOTO", "图片保存失败");
        }
        return "/api/review-photos/" + filename;
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
        return path.startsWith("/api/review-photos/") && !path.contains("..");
    }

    private String normalizeType(MultipartFile file) {
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
