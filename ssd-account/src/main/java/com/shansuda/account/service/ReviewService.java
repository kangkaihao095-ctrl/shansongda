package com.shansuda.account.service;

import com.shansuda.account.domain.AppUser;
import com.shansuda.account.domain.MerchantReview;
import com.shansuda.account.domain.MerchantSku;
import com.shansuda.account.domain.ReviewLike;
import com.shansuda.account.repo.AppUserRepo;
import com.shansuda.account.repo.MerchantRepo;
import com.shansuda.account.repo.MerchantReviewRepo;
import com.shansuda.account.repo.MerchantSkuRepo;
import com.shansuda.account.repo.ReviewLikeRepo;
import com.shansuda.account.config.ReviewPhotoStorage;
import com.shansuda.common.api.BizException;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final MerchantReviewRepo reviewRepo;
    private final ReviewLikeRepo likeRepo;
    private final MerchantRepo merchantRepo;
    private final MerchantSkuRepo skuRepo;
    private final AppUserRepo userRepo;
    private final MemberService memberService;
    private final ReviewPhotoStorage photoStorage;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ReviewService(MerchantReviewRepo reviewRepo, ReviewLikeRepo likeRepo, MerchantRepo merchantRepo,
                         MerchantSkuRepo skuRepo, AppUserRepo userRepo, MemberService memberService,
                         ReviewPhotoStorage photoStorage, ObjectMapper objectMapper, DataSource dataSource) {
        this.reviewRepo = reviewRepo;
        this.likeRepo = likeRepo;
        this.merchantRepo = merchantRepo;
        this.skuRepo = skuRepo;
        this.userRepo = userRepo;
        this.memberService = memberService;
        this.photoStorage = photoStorage;
        this.objectMapper = objectMapper;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Map<String, Object> list(long merchantId, int page, int size) {
        int p = Math.max(1, page);
        int sz = Math.min(30, Math.max(5, size));
        Page<MerchantReview> result = reviewRepo.findByMerchantIdOrderByCreatedAtDesc(
                merchantId, PageRequest.of(p - 1, sz));
        AuthUser auth = AuthHolder.get();
        Long me = auth == null ? null : auth.userId();
        Set<Long> liked = Set.of();
        if (me != null && !result.isEmpty()) {
            List<Long> ids = result.getContent().stream().map(MerchantReview::getId).toList();
            liked = likeRepo.findAllById(ids.stream().map(id -> new ReviewLike.Pk(me, id)).toList())
                    .stream().map(ReviewLike::getReviewId).collect(Collectors.toSet());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (MerchantReview r : result.getContent()) {
            items.add(view(r, liked.contains(r.getId())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p);
        body.put("size", sz);
        body.put("total", result.getTotalElements());
        body.put("hasNext", result.hasNext());
        return body;
    }

    public Map<String, Object> mine(int page, int size) {
        AuthUser auth = AuthHolder.require();
        int p = Math.max(1, page);
        int sz = Math.min(30, Math.max(5, size));
        Page<MerchantReview> result = reviewRepo.findByUserIdOrderByCreatedAtDesc(
                auth.userId(), PageRequest.of(p - 1, sz));
        Set<Long> liked = Set.of();
        if (!result.isEmpty()) {
            List<Long> ids = result.getContent().stream().map(MerchantReview::getId).toList();
            liked = likeRepo.findAllById(ids.stream().map(id -> new ReviewLike.Pk(auth.userId(), id)).toList())
                    .stream().map(ReviewLike::getReviewId).collect(Collectors.toSet());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (MerchantReview r : result.getContent()) {
            Map<String, Object> row = view(r, liked.contains(r.getId()));
            merchantRepo.findById(r.getMerchantId()).ifPresent(m -> {
                row.put("shopName", m.getShopName());
                row.put("shopCover", m.getCoverUrl());
                row.put("category", m.getCategory());
            });
            items.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p);
        body.put("size", sz);
        body.put("total", result.getTotalElements());
        body.put("hasNext", result.hasNext());
        return body;
    }

    public Map<String, Object> savePhoto(MultipartFile file) {
        AuthUser auth = AuthHolder.require();
        String url = photoStorage.save(auth.userId(), file);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("photoUrl", url);
        return body;
    }

    @Transactional
    public Map<String, Object> create(long userId, long orderId, long merchantId, int score, String content,
                                      List<Long> skuIds, List<String> photoUrls, Integer riderScore) {
        if (score < 1 || score > 5) {
            throw BizException.badRequest("BAD_SCORE", "请给商家点星");
        }
        if (riderScore != null && (riderScore < 1 || riderScore > 5)) {
            throw BizException.badRequest("BAD_RIDER_SCORE", "请给骑手点星");
        }
        String text = content == null ? "" : content.trim();
        if (text.length() > 512) {
            throw BizException.badRequest("BAD_CONTENT", "评价不能超过 512 字");
        }
        List<String> photos = sanitizePhotos(photoUrls);
        if (reviewRepo.existsByOrderId(orderId)) {
            throw BizException.conflict("REVIEWED", "该订单已评价");
        }
        merchantRepo.findById(merchantId).orElseThrow(() -> BizException.notFound("商家不存在"));
        String skuNames = resolveSkuNames(merchantId, skuIds);
        MerchantReview review = new MerchantReview();
        review.setMerchantId(merchantId);
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setScore(score);
        review.setContent(text);
        review.setSkuNames(skuNames);
        review.setLikeCount(0);
        review.setPhotoUrls(writePhotos(photos));
        review.setRiderScore(riderScore);
        review.setCreatedAt(Instant.now());
        try {
            review = reviewRepo.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            throw BizException.conflict("REVIEWED", "该订单已评价");
        }
        refreshMerchantRating(merchantId);
        return view(review, false);
    }

    @Transactional
    public Map<String, Object> toggleLike(long reviewId) {
        AuthUser auth = AuthHolder.require();
        MerchantReview review = reviewRepo.findById(reviewId).orElseThrow(() -> BizException.notFound("评价不存在"));
        boolean liked;
        if (likeRepo.existsByUserIdAndReviewId(auth.userId(), reviewId)) {
            likeRepo.deleteByUserIdAndReviewId(auth.userId(), reviewId);
            jdbc.update("UPDATE merchant_review SET like_count = GREATEST(COALESCE(like_count,0) - 1, 0) WHERE id=?", reviewId);
            liked = false;
        } else {
            try {
                ReviewLike row = new ReviewLike();
                row.setUserId(auth.userId());
                row.setReviewId(reviewId);
                row.setCreatedAt(Instant.now());
                likeRepo.saveAndFlush(row);
                jdbc.update("UPDATE merchant_review SET like_count = COALESCE(like_count,0) + 1 WHERE id=?", reviewId);
                liked = true;
            } catch (DataIntegrityViolationException ex) {
                liked = true;
            }
        }
        Integer count = jdbc.queryForObject("SELECT like_count FROM merchant_review WHERE id=?", Integer.class, reviewId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewId", review.getId());
        body.put("likeCount", count == null ? 0 : count);
        body.put("likedByMe", liked);
        return body;
    }

    public Map<String, Object> byOrder(long orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        var found = reviewRepo.findByOrderId(orderId);
        body.put("reviewed", found.isPresent());
        found.ifPresent(r -> {
            body.put("reviewId", r.getId());
            body.put("review", view(r, false));
        });
        return body;
    }

    private void refreshMerchantRating(long merchantId) {
        jdbc.update("""
                UPDATE merchant m
                JOIN (
                  SELECT merchant_id, AVG(score) avg_s, COUNT(*) cnt
                  FROM merchant_review WHERE merchant_id=? GROUP BY merchant_id
                ) r ON m.user_id = r.merchant_id
                SET m.rating_avg = r.avg_s, m.rating_count = r.cnt, m.rating = r.avg_s
                """, merchantId);
    }

    private String resolveSkuNames(long merchantId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return skuRepo.findByMerchantId(merchantId).stream().limit(2)
                    .map(MerchantSku::getName).collect(Collectors.joining("、"));
        }
        return skuIds.stream()
                .map(id -> skuRepo.findById(id).map(MerchantSku::getName).orElse(null))
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.joining("、"));
    }

    private Map<String, Object> view(MerchantReview review, boolean likedByMe) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", review.getId());
        body.put("merchantId", review.getMerchantId());
        body.put("orderId", review.getOrderId());
        body.put("score", review.getScore());
        body.put("content", review.getContent());
        body.put("skuNames", review.getSkuNames());
        body.put("photoUrls", parsePhotos(review.getPhotoUrls()));
        body.put("riderScore", review.getRiderScore());
        body.put("likeCount", review.getLikeCount() == null ? 0 : review.getLikeCount());
        body.put("likedByMe", likedByMe);
        body.put("createdAt", review.getCreatedAt());
        body.put("anonymousName", anonymousName(review.getUserId()));
        Map<String, Object> badge = memberService.badge(review.getUserId());
        if (badge != null) {
            body.put("member", badge);
        }
        return body;
    }

    private String anonymousName(Long userId) {
        if (userId == null) {
            return "闪送用户";
        }
        AppUser user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return "闪送用户";
        }
        String name = user.getDisplayName();
        if (name != null && !name.isBlank()) {
            String n = name.trim();
            return n.charAt(0) + "**";
        }
        String phone = user.getPhone() == null ? "" : user.getPhone();
        if (phone.length() >= 4) {
            return "用户" + phone.substring(phone.length() - 4);
        }
        return "闪送用户";
    }

    private List<String> sanitizePhotos(List<String> photoUrls) {
        if (photoUrls == null || photoUrls.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String url : photoUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String trimmed = url.trim();
            if (!photoStorage.acceptedUrl(trimmed)) {
                throw BizException.badRequest("BAD_PHOTO", "评价配图无效");
            }
            out.add(trimmed);
            if (out.size() > 3) {
                throw BizException.badRequest("BAD_PHOTO", "最多上传 3 张配图");
            }
        }
        return out;
    }

    private String writePhotos(List<String> photos) {
        if (photos == null || photos.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(photos);
        } catch (Exception ex) {
            throw BizException.badRequest("BAD_PHOTO", "评价配图无效");
        }
    }

    private List<String> parsePhotos(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }
}
