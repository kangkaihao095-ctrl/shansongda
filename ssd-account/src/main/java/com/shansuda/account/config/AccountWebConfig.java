package com.shansuda.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AccountWebConfig implements WebMvcConfigurer {

    private final AvatarStorage avatarStorage;
    private final ReviewPhotoStorage reviewPhotoStorage;

    public AccountWebConfig(AvatarStorage avatarStorage, ReviewPhotoStorage reviewPhotoStorage) {
        this.avatarStorage = avatarStorage;
        this.reviewPhotoStorage = reviewPhotoStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/avatars/**").addResourceLocations(dir(avatarStorage.root().toUri().toString()));
        registry.addResourceHandler("/api/review-photos/**")
                .addResourceLocations(dir(reviewPhotoStorage.root().toUri().toString()));
    }

    private static String dir(String location) {
        return location.endsWith("/") ? location : location + "/";
    }
}
