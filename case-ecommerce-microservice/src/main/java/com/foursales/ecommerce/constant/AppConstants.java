package com.foursales.ecommerce.constant;

import java.math.RoundingMode;

public final class AppConstants {

    private AppConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // Kafka Topics
    public static final String TOPIC_ORDER_PAID = "order.paid";
    public static final String TOPIC_PRODUCT_SYNC = "product.sync";

    // Report Constants
    public static final int TOP_USERS_LIMIT = 5;
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final int DECIMAL_SCALE = 2;

    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}
