package com.shansuda.common.catalog;

import java.util.Locale;

/**
 * 门头 / 商品图一律走本站真实照片 JPG，禁止 picsum / loremflickr 外链。
 * 门头 {@code /images/shops/{merchantId}.jpg}；菜品 {@code /images/dishes/{category}/{1..80}.jpg}。
 * 启动时若本地文件已存在则写入这些路径。onerror 落到 {@code /images/ph/{category}.jpg}。
 */
public final class CatalogImages {

    public static final int SHOP_POOL = 15;
    public static final int DISH_POOL = 80;

    private CatalogImages() {
    }

    public static String shopCover(long merchantId) {
        return shopCover(merchantId, "food");
    }

    public static String shopCover(long merchantId, String category) {
        return "/images/shops/" + merchantId + ".jpg";
    }

    public static String skuImage(long skuId) {
        return skuImage(skuId, "food", null, null);
    }

    public static String skuImage(long skuId, String category, String groupName, String skuName) {
        String cat = norm(category);
        int slot = Math.floorMod(lock(skuId, 29), DISH_POOL) + 1;
        return "/images/dishes/" + cat + "/" + slot + ".jpg";
    }

    /** 兼容旧调用：不再出外链，与 {@link #shopCover(long, String)} 同口径。 */
    public static String remoteShop(long merchantId) {
        return shopCover(merchantId, "food");
    }

    public static String remoteShop(long merchantId, String category) {
        return shopCover(merchantId, category);
    }

    public static String remoteSku(long skuId) {
        return skuImage(skuId, "food", null, null);
    }

    public static String remoteSku(long skuId, String category, String groupName, String skuName) {
        return skuImage(skuId, category, groupName, skuName);
    }

    public static String placeholder(String category) {
        return "/images/ph/" + norm(category) + ".jpg";
    }

    public static String shopKeywords(String category) {
        return switch (norm(category)) {
            case "dessert" -> "bakery,cafe,dessert";
            case "market" -> "grocery,convenience-store,supermarket";
            case "fresh" -> "fruit,vegetables,produce";
            case "pharma" -> "pharmacy,drugstore,medicine";
            case "flower" -> "flowers,florist,bouquet";
            case "tea" -> "milk-tea,cafe,tea";
            case "errand" -> "parcel,courier,package";
            default -> "chinese-food,restaurant,chinese-restaurant";
        };
    }

    public static String skuKeywords(String category, String groupName, String skuName) {
        String cat = norm(category);
        String blob = ((groupName == null ? "" : groupName) + " " + (skuName == null ? "" : skuName));
        return switch (cat) {
            case "pharma" -> "pharmacy,medicine,pills";
            case "errand" -> "parcel,package,courier";
            case "fresh" -> freshKeywords(blob);
            case "flower" -> flowerKeywords(blob);
            case "dessert" -> dessertKeywords(blob);
            case "market" -> marketKeywords(blob);
            case "tea" -> teaKeywords(blob);
            case "food" -> foodKeywords(blob);
            default -> shopKeywords(cat);
        };
    }

    private static String foodKeywords(String blob) {
        if (contains(blob, "饭", "面", "粉")) {
            return "chinese-food,noodles,rice";
        }
        if (contains(blob, "汤")) {
            return "chinese-food,soup";
        }
        if (contains(blob, "凉", "拌")) {
            return "chinese-food,salad";
        }
        return "chinese-food,dish,chinese-cuisine";
    }

    private static String dessertKeywords(String blob) {
        if (contains(blob, "蛋糕", "挞", "酥", "贝")) {
            return "bakery,cake,pastry";
        }
        if (contains(blob, "咖啡", "拿铁")) {
            return "coffee,cafe,latte";
        }
        return "bakery,milk-tea,dessert";
    }

    private static String marketKeywords(String blob) {
        if (contains(blob, "巾", "洗衣液", "牙刷", "袋", "伞", "拖鞋", "毛巾", "口罩", "电池")) {
            return "grocery,toiletries,household";
        }
        if (contains(blob, "水", "可乐", "雪碧", "奶", "茶", "汁")) {
            return "grocery,beverage,bottle";
        }
        return "grocery,supermarket,convenience-store";
    }

    private static String freshKeywords(String blob) {
        if (contains(blob, "肉", "鸡", "排骨", "牛", "羊")) {
            return "meat,butcher,fresh";
        }
        if (contains(blob, "虾", "鱼", "花甲", "海鲜")) {
            return "seafood,fish,shrimp";
        }
        if (contains(blob, "菜", "茄", "瓜", "椒", "菇", "芹", "萝", "豆")) {
            return "vegetables,produce";
        }
        return "fruit,fresh-fruit,produce";
    }

    private static String flowerKeywords(String blob) {
        if (contains(blob, "蛋糕", "慕斯", "马卡龙", "提拉")) {
            return "cake,bakery,dessert";
        }
        return "flowers,bouquet,florist";
    }

    private static String teaKeywords(String blob) {
        if (contains(blob, "可颂", "司康", "三明治", "贝果", "意面", "卷饼", "沙拉", "吐司")) {
            return "sandwich,pastry,cafe-food";
        }
        if (contains(blob, "蛋糕", "塔", "布朗尼")) {
            return "cake,pastry,dessert";
        }
        if (contains(blob, "咖啡", "美式", "拿铁", "摩卡", "冷萃", "澳白")) {
            return "coffee,latte,cafe";
        }
        return "milk-tea,tea,cafe";
    }

    public static String norm(String category) {
        if (category == null || category.isBlank()) {
            return "food";
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }

    public static int lock(long id, int salt) {
        long mixed = id * 1_000_003L + (long) salt * 97L;
        return (int) Math.floorMod(mixed, 1_000_000_000L);
    }

    private static boolean contains(String blob, String... keys) {
        for (String key : keys) {
            if (blob.contains(key)) {
                return true;
            }
        }
        return false;
    }

}
