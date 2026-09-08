package com.shansuda.account;

import com.shansuda.common.catalog.CatalogImages;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogImagesTest {

    @Test
    void shopAndSkuUseLocalJpegNotRemote() {
        Set<String> shops = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (long id = 1; id <= 120; id++) {
            String url = CatalogImages.shopCover(id, "food");
            shops.add(url);
            assertTrue(url.endsWith(".jpg"));
            assertFalse(url.contains("loremflickr"));
            assertFalse(url.contains("picsum"));
            assertFalse(url.contains(".svg"));
            assertFalse(url.startsWith("http"));
        }
        for (long id = 100000; id < 100000 + 5040; id++) {
            String url = CatalogImages.skuImage(id, "food", "招牌", "红烧肉");
            skus.add(url);
            assertTrue(url.startsWith("/images/dishes/food/"));
            assertTrue(url.endsWith(".jpg"));
            assertFalse(url.contains("http"));
            assertFalse(url.contains(".svg"));
        }
        assertEquals(120, shops.size());
        assertEquals(CatalogImages.DISH_POOL, skus.size());
        assertTrue(CatalogImages.shopCover(3, "food").endsWith(".jpg"));
    }

    @Test
    void categoriesStayInOwnFolders() {
        String food = CatalogImages.skuImage(3, "food", "热菜", "红烧肉");
        String pharma = CatalogImages.skuImage(17, "pharma", "常用药", "感冒清热颗粒");
        String flower = CatalogImages.skuImage(18, "flower", "鲜花", "韩式花束");
        String errand = CatalogImages.skuImage(2001, "errand", "代取", "代取快递");
        assertTrue(food.startsWith("/images/dishes/food/"));
        assertTrue(pharma.startsWith("/images/dishes/pharma/"));
        assertTrue(flower.startsWith("/images/dishes/flower/"));
        assertTrue(errand.startsWith("/images/dishes/errand/"));
        assertFalse(pharma.contains("/food/"));
        assertFalse(food.contains("/pharma/"));
        assertFalse(errand.contains("loremflickr"));
        assertTrue(CatalogImages.shopCover(17, "pharma").endsWith(".jpg"));
        assertTrue(CatalogImages.placeholder("pharma").endsWith("/images/ph/pharma.jpg"));
        assertEquals("pharmacy,drugstore,medicine", CatalogImages.shopKeywords("pharma"));
        assertEquals("chinese-food,restaurant,chinese-restaurant", CatalogImages.shopKeywords("food"));
    }
}
