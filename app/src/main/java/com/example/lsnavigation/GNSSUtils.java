/*
 * GNSS用のユーティリティクラス
 */
package com.example.lsnavigation;

import org.osmdroid.util.GeoPoint;

public final class GNSSUtils {
    // debug用TAG
    private static final String GNSS_DEBUG_TAG = "GNSS";
    // ----世界観測値系----
    public static final double GRS80_A = 6378137.000;//長半径 a(m)
    public static final double GRS80_E2 = 0.00669438002301188;//第一遠心率  eの2乗
    // -----------------
    // debug用座標UEC
    public static final double homePointLat = 35.6587588;
    public static final double homePointLon = 139.5434757;
    public static final double pylonPointLat = 36.6587588;
    public static final double pylonPointLon = 138.5434757;
    // -----------
    // プラットホーム座標
    public static GeoPoint homePoint = new GeoPoint(homePointLat, homePointLon);
    public static GeoPoint pylonPoint = new GeoPoint(pylonPointLat, pylonPointLon);
    public static GeoPoint centerPoint;   // 現在値

    public static int distanceHome = 10;
    public static int distancePylon = 10;
    public static int groundSpeed = 0;
    private static double deg2rad(double deg){
        return deg * Math.PI / 180.0;
    }

    private static double calcDistance(double lat1, double lng1, double lat2, double lng2){
        double my = deg2rad((lat1 + lat2) / 2.0); //緯度の平均値
        double dy = deg2rad(lat1 - lat2); //緯度の差
        double dx = deg2rad(lng1 - lng2); //経度の差

        //卯酉線曲率半径を求める(東と西を結ぶ線の半径)
        double sinMy = Math.sin(my);
        double w = Math.sqrt(1.0 - GRS80_E2 * sinMy * sinMy);
        double n = GRS80_A / w;

        //子午線曲線半径を求める(北と南を結ぶ線の半径)
        double mnum = GRS80_A * (1 - GRS80_E2);
        double m = mnum / (w * w * w);

        //ヒュベニの公式
        double dym = dy * m;
        double dxncos = dx * n * Math.cos(my);
        return Math.sqrt(dym * dym + dxncos * dxncos);
    }
}
