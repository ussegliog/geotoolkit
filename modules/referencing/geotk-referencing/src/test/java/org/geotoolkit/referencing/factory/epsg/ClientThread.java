/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2002-2012, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2009-2012, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotoolkit.referencing.factory.epsg;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.awt.geom.Point2D;

import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.ProjectedCRS;

import org.apache.sis.math.Statistics;
import org.geotoolkit.referencing.CRS;
import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.geotoolkit.referencing.factory.AbstractAuthorityFactory;
import org.apache.sis.geometry.DirectPosition2D;

import static org.junit.Assert.*;
import static java.lang.StrictMath.*;
import static org.apache.sis.util.collection.Containers.hashMapCapacity;


/**
 * A thread which will perform many coordinate transformations between different pairs of CRS.
 * The results are compared with previous results for consistency.
 * <p>
 * The {@link StressTest} class starts many instances of this class in order to test concurrency.
 *
 * @author Jody Garnett (Refractions)
 * @author Martin Desruisseaux (Geomatys)
 * @version 3.17
 *
 * @since 2.4
 */
final strictfp class ClientThread extends Thread {
    /**
     * The tolerance (in decimal degrees) between the results of transformation
     * to geographic coordinates.
     */
    private static final double ANGLE_TOLERANCE = 1E-12;

    /**
     * The squared value of the maximal distance (in metres) allowed between a point
     * in UTM 84 and its transform in UTM 72, or conversely.
     */
    private static final double UTM_TOLERANCE = 20 * 20;

    /**
     * Thread identifier.
     */
    final int id;

    /**
     * The result of the {@link #testGeographicToRandom()} method.
     * Keys are EPSG code of the source CRS. Values are the transformed coordinates.
     */
    final Map<Integer, Point2D.Double> result;

    /**
     * The first exception that occurred, or {@code null} if everything was successful.
     */
    Throwable exception;

    /**
     * The code of the CRS that caused the exception, or {@code null} if none.
     */
    String badCode;

    /**
     * Test metrics.
     */
    final Statistics statistics;

    /**
     * Random number generator.
     */
    private final Random random;

    /**
     * The factory to test.
     */
    private final AbstractAuthorityFactory factory;

    /**
     * Used in order to wait until every threads are ready to process.
     */
    private final CountDownLatch lock;

    /**
     * Creates a client thread for the given factory to test.
     *
     * @param id Thread identifier.
     * @param factory The factory to test.
     */
    public ClientThread(final int id, final AbstractAuthorityFactory factory, final CountDownLatch lock) {
        super("ClientThread" + (id < 10 ? " 0" : " ") + id);
        this.id         = id;
        this.factory    = factory;
        this.random     = new Random(179702531L + id);
        this.statistics = new Statistics(null);
        this.lock       = lock;
        this.result     = createEmptyResultMap();
    }

    /**
     * Creates an initially empty map which will hold the result of
     * {@link #testGeographicToRandom()}.
     */
    static Map<Integer, Point2D.Double> createEmptyResultMap() {
        return new HashMap<>(hashMapCapacity(CODES.length));
    }

    /**
     * Gets random CRS and apply a map projection from that CRS to WGS84 for each of them.
     */
    @Override
    public void run() {
        final CoordinateReferenceSystem sourceCRS = DefaultGeographicCRS.WGS84;
        final DirectPosition2D sourcePt = new DirectPosition2D(104800, 273914); // Random coordinate
        final DirectPosition2D targetPt = new DirectPosition2D();
        final DirectPosition2D coordUTM = new DirectPosition2D();
        /*
         * Wait until every threads are ready to process.
         */
        try {
            lock.await();
        } catch (InterruptedException e) {
            // Consider that as a failure.
            exception = e;
            return;
        }
        for (int i=0; i<StressTest.ITERATIONS; i++) {
            /*
             * Test the transformations from a random CRS to WGS84. This test uses a
             * fixed coordinates so we can verify that every thread produce the same
             * result for the same source CRS.
             */
            final int crsCode = CODES[random.nextInt(CODES.length)];
            String code = String.valueOf(crsCode);
            long startTime = System.nanoTime();
            try {
                /*
                 * Random CRS --> WGS84
                 * The result will be saved for comparison with the results from other threads.
                 */
                final CoordinateReferenceSystem targetCRS = factory.createCoordinateReferenceSystem(code);
                MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
                assertSame(targetPt, transform.inverse().transform(sourcePt, targetPt));
                statistics.accept(System.nanoTime() - startTime);
                assertFalse("x=NaN", Double.isNaN(targetPt.x));
                assertFalse("y=NaN", Double.isNaN(targetPt.y));
                assertConsistent(result, crsCode, targetPt);
                /*
                 * UTM 72 --> UTM 84 (or conversely)
                 * The distance should be small.
                 */
                int zone = random.nextInt(60) + 1;
                if (random.nextBoolean()) {
                    zone += 100; // Switch from zone North to zone South.
                }
                final ProjectedCRS sourceUTM = factory.createProjectedCRS(code = String.valueOf(32200 + zone));
                final ProjectedCRS targetUTM = factory.createProjectedCRS(code = String.valueOf(32600 + zone));
                transform = CRS.findMathTransform(sourceUTM, targetUTM);
                if (random.nextBoolean()) {
                    transform = transform.inverse();
                }
                assertFalse("Expected at datum shift.", transform.isIdentity());
                final double x = coordUTM.x = random.nextDouble() *  300000 +  100000;
                final double y = coordUTM.y = random.nextDouble() * 5000000 + 2500000;
                assertSame(coordUTM, transform.transform(coordUTM, coordUTM));
                final double distSq = coordUTM.distanceSq(x, y);
                if (!(distSq <= UTM_TOLERANCE)) { // Use '!' for catching NaN.
                    fail("UTM tolerance error: " + sqrt(distSq));
                }
            } catch (Throwable e) {
                exception = e;
                badCode = code;
                break;
            }
        }
    }

    /**
     * Asserts that the given result of a coordinate transformation is consistent with previous
     * result.
     */
    static void assertConsistent(final Map<Integer, Point2D.Double> result,
            final Integer code, final Point2D.Double point)
    {
        final Point2D.Double previous = result.put(code, new Point2D.Double(point.x, point.y));
        if (previous != null) {
            assertEquals("x", previous.x, point.x, ANGLE_TOLERANCE);
            assertEquals("y", previous.y, point.y, ANGLE_TOLERANCE);
        }
    }

    /**
     * A selection of CRS codes for UTM and NAD zones.
     */
    static final int[] CODES = {
             2008,  2009,  2010,  2011,  2012,  2013,  2014,  2015,  2016,  2017,  2018,  2019,
             2020,  2021,  2022,  2023,  2024,  2025,  2026,  2027,  2028,  2029,  2030,  2031,
             2032,  2033,  2034,  2035,  2040,  2041,  2042,  2043,  2058,  2059,  2060,  2061,
             2063,  2064,  2067,  2077,  2078,  2079,  2080,  2084,  2085,  2086,  2089,  2090,
             2095,  2133,  2134,  2135,  2138,  2156,  2158,  2161,  2162,  2188,  2189,  2190,
             2195,  2201,  2202,  2203,  2204,  2205,  2215,  2216,  2217,  2219,  2220,  2222,
             2223,  2224,  2225,  2226,  2227,  2228,  2229,  2230,  2231,  2232,  2233,  2234,
             2235,  2236,  2237,  2238,  2239,  2240,  2241,  2242,  2243,  2244,  2245,  2246,
             2247,  2248,  2249,  2250,  2251,  2252,  2253,  2254,  2255,  2256,  2257,  2258,
             2259,  2260,  2261,  2262,  2263,  2264,  2265,  2266,  2267,  2268,  2269,  2270,
             2271,  2272,  2273,  2274,  2275,  2276,  2277,  2278,  2279,  2280,  2281,  2282,
             2283,  2284,  2285,  2286,  2287,  2288,  2289,  2291,  2312,  2313,  2315,  2316,
             2736,  2737,  2759,  2760,  2761,  2762,  2763,  2764,  2765,  2766,  2767,  2768,
             2769,  2770,  2771,  2772,  2773,  2774,  2775,  2776,  2777,  2778,  2779,  2780,
             2781,  2782,  2783,  2784,  2785,  2786,  2787,  2788,  2789,  2790,  2791,  2792,
             2793,  2794,  2795,  2796,  2797,  2798,  2799,  2800,  2801,  2802,  2803,  2804,
             2805,  2806,  2807,  2808,  2809,  2810,  2811,  2812,  2813,  2814,  2815,  2816,
             2817,  2818,  2819,  2820,  2821,  2822,  2823,  2824,  2825,  2826,  2827,  2828,
             2829,  2830,  2831,  2832,  2833,  2834,  2835,  2836,  2837,  2838,  2839,  2840,
             2841,  2842,  2843,  2844,  2845,  2846,  2847,  2848,  2849,  2850,  2851,  2852,
             2853,  2854,  2855,  2856,  2857,  2858,  2859,  2860,  2861,  2862,  2863,  2864,
             2865,  2866,  2867,  2868,  2869,  2870,  2871,  2872,  2873,  2874,  2875,  2876,
             2877,  2878,  2879,  2880,  2881,  2882,  2883,  2884,  2885,  2886,  2887,  2888,
             2889,  2890,  2891,  2892,  2893,  2894,  2895,  2896,  2897,  2898,  2899,  2900,
             2901,  2902,  2903,  2904,  2905,  2906,  2907,  2908,  2909,  2910,  2911,  2912,
             2913,  2914,  2915,  2916,  2917,  2918,  2919,  2920,  2921,  2922,  2923,  2924,
             2925,  2926,  2927,  2928,  2929,  2930,  2933,  2936,  2942,  2943,  2944,  2945,
             2946,  2947,  2948,  2949,  2950,  2951,  2952,  2953,  2954,  2955,  2956,  2957,
             2958,  2959,  2960,  2961,  2962,  2964,  2965,  2966,  2967,  2968,  2969,  2970,
             2971,  2972,  2973,  2975,  2976,  2977,  2978,  2979,  2980,  2981,  2983,  2987,
             2988,  2989,  2991,  2992,  2993,  2994,  2995,  2996,  2997,  2998,  2999,  3005,
             3036,  3037,  3054,  3055,  3056,  3060,  3061,  3062,  3063,  3064,  3065,  3148,
             3149,  3439,  3440,  3920,  4152,  4267,  4268,  4269,  4608,  4609,  4617, 20135,
            20136, 20137, 20138, 20437, 20438, 20439, 20538, 20539, 20822, 20823, 20824, 20934,
            20935, 20936, 21035, 21036, 21037, 21095, 21096, 21097, 21148, 21149, 21150, 21817,
            21818, 22032, 22033, 22234, 22235, 22236, 22332, 22523, 22524, 23028, 23029, 23030,
            23031, 23032, 23033, 23034, 23035, 23036, 23037, 23038, 23239, 23240, 23846, 23847,
            23848, 23849, 23850, 23851, 23852, 23853, 23886, 23887, 23888, 23889, 23890, 23891,
            23892, 23893, 23894, 23946, 23947, 23948, 24047, 24048, 24305, 24306, 24311, 24312,
            24313, 24342, 24343, 24344, 24345, 24346, 24347, 24547, 24548, 24718, 24719, 24720,
            24818, 24819, 24820, 24821, 24877, 24878, 24879, 24880, 24882, 25231, 25828, 25829,
            25830, 25831, 25832, 25833, 25834, 25835, 25836, 25837, 25838, 25932, 26237, 26331,
            26332, 26432, 26632, 26692, 26703, 26704, 26705, 26706, 26707, 26708, 26709, 26710,
            26711, 26712, 26713, 26714, 26715, 26716, 26717, 26718, 26719, 26720, 26721, 26722,
            26729, 26730, 26731, 26732, 26733, 26734, 26735, 26736, 26737, 26738, 26739, 26740,
            26741, 26742, 26743, 26744, 26745, 26746, 26747, 26748, 26749, 26750, 26751, 26752,
            26753, 26754, 26755, 26756, 26757, 26758, 26759, 26760, 26766, 26767, 26768, 26769,
            26770, 26771, 26772, 26773, 26774, 26775, 26776, 26777, 26778, 26779, 26780, 26781,
            26782, 26783, 26784, 26785, 26786, 26787, 26791, 26792, 26793, 26794, 26795, 26796,
            26797, 26798, 26801, 26802, 26803, 26811, 26812, 26813, 26903, 26904, 26905, 26906,
            26907, 26908, 26909, 26910, 26911, 26912, 26913, 26914, 26915, 26916, 26917, 26918,
            26919, 26920, 26921, 26922, 26923, 26929, 26930, 26931, 26932, 26933, 26934, 26935,
            26936, 26937, 26938, 26939, 26940, 26941, 26942, 26943, 26944, 26945, 26946, 26948,
            26949, 26950, 26951, 26952, 26953, 26954, 26955, 26956, 26957, 26958, 26959, 26960,
            26961, 26962, 26963, 26964, 26965, 26966, 26967, 26968, 26969, 26970, 26971, 26972,
            26973, 26974, 26975, 26976, 26977, 26978, 26979, 26980, 26981, 26982, 26983, 26984,
            26985, 26986, 26987, 26988, 26989, 26990, 26991, 26992, 26993, 26994, 26995, 26996,
            26997, 26998, 27038, 27039, 27040, 27120, 27258, 27259, 27260, 27429, 28232, 29168,
            29169, 29170, 29171, 29172, 29187, 29188, 29189, 29190, 29191, 29192, 29193, 29194,
            29195, 29220, 29221, 29333, 29738, 29739, 29849, 29850, 30339, 30340, 30729, 30730,
            30731, 30732, 31028, 31121, 31528, 31529, 31838, 31839, 31986, 31987, 31988, 31989,
            31990, 31991, 31992, 31993, 31994, 31995, 31996, 31997, 31998, 31999, 32000, 32001,
            32002, 32003, 32005, 32006, 32007, 32008, 32009, 32010, 32011, 32012, 32013, 32014,
            32015, 32016, 32017, 32018, 32019, 32020, 32021, 32022, 32023, 32024, 32025, 32026,
            32027, 32028, 32029, 32030, 32031, 32033, 32034, 32035, 32036, 32037, 32038, 32039,
            32040, 32041, 32042, 32043, 32044, 32045, 32046, 32047, 32048, 32049, 32050, 32051,
            32052, 32053, 32054, 32055, 32056, 32057, 32058, 32061, 32062, 32064, 32065, 32066,
            32067, 32074, 32075, 32076, 32077, 32081, 32082, 32083, 32084, 32085, 32086, 32098,
            32100, 32104, 32107, 32108, 32109, 32110, 32111, 32112, 32113, 32114, 32115, 32116,
            32117, 32118, 32119, 32120, 32121, 32122, 32123, 32124, 32125, 32126, 32127, 32128,
            32129, 32130, 32133, 32134, 32135, 32136, 32137, 32138, 32139, 32140, 32141, 32142,
            32143, 32144, 32145, 32146, 32147, 32148, 32149, 32150, 32151, 32152, 32153, 32154,
            32155, 32156, 32157, 32158, 32161, 32180, 32181, 32182, 32183, 32184, 32185, 32186,
            32187, 32188, 32189, 32190, 32191, 32192, 32193, 32194, 32195, 32196, 32197, 32198,
            32201, 32202, 32203, 32204, 32205, 32206, 32207, 32208, 32209, 32210, 32211, 32212,
            32213, 32214, 32215, 32216, 32217, 32218, 32219, 32220, 32221, 32222, 32223, 32224,
            32225, 32226, 32227, 32228, 32229, 32230, 32231, 32232, 32233, 32234, 32235, 32236,
            32237, 32238, 32239, 32240, 32241, 32242, 32243, 32244, 32245, 32246, 32247, 32248,
            32249, 32250, 32251, 32252, 32253, 32254, 32255, 32256, 32257, 32258, 32259, 32260,
            32301, 32302, 32303, 32304, 32305, 32306, 32307, 32308, 32309, 32310, 32311, 32312,
            32313, 32314, 32315, 32316, 32317, 32318, 32319, 32320, 32321, 32322, 32323, 32324,
            32325, 32326, 32327, 32328, 32329, 32330, 32331, 32332, 32333, 32334, 32335, 32336,
            32337, 32338, 32339, 32340, 32341, 32342, 32343, 32344, 32345, 32346, 32347, 32348,
            32349, 32350, 32351, 32352, 32353, 32354, 32355, 32356, 32357, 32358, 32359, 32360,
            32401, 32402, 32403, 32404, 32405, 32406, 32407, 32408, 32409, 32410, 32411, 32412,
            32413, 32414, 32415, 32416, 32417, 32418, 32419, 32420, 32421, 32422, 32423, 32424,
            32425, 32426, 32427, 32428, 32429, 32430, 32431, 32432, 32433, 32434, 32435, 32436,
            32437, 32438, 32439, 32440, 32441, 32442, 32443, 32444, 32445, 32446, 32447, 32448,
            32449, 32450, 32451, 32452, 32453, 32454, 32455, 32456, 32457, 32458, 32459, 32460,
            32501, 32502, 32503, 32504, 32505, 32506, 32507, 32508, 32509, 32510, 32511, 32512,
            32513, 32514, 32515, 32516, 32517, 32518, 32519, 32520, 32521, 32522, 32523, 32524,
            32525, 32526, 32527, 32528, 32529, 32530, 32531, 32532, 32533, 32534, 32535, 32536,
            32537, 32538, 32539, 32540, 32541, 32542, 32543, 32544, 32545, 32546, 32547, 32548,
            32549, 32550, 32551, 32552, 32553, 32554, 32555, 32556, 32557, 32558, 32559, 32560,
            32601, 32602, 32603, 32604, 32605, 32606, 32607, 32608, 32609, 32610, 32611, 32612,
            32613, 32614, 32615, 32616, 32617, 32618, 32619, 32620, 32621, 32622, 32623, 32624,
            32625, 32626, 32627, 32628, 32629, 32630, 32631, 32632, 32633, 32634, 32635, 32636,
            32637, 32638, 32639, 32640, 32641, 32642, 32643, 32644, 32645, 32646, 32647, 32648,
            32649, 32650, 32651, 32652, 32653, 32654, 32655, 32656, 32657, 32658, 32659, 32660,
            32701, 32702, 32703, 32704, 32705, 32706, 32707, 32708, 32709, 32710, 32711, 32712,
            32713, 32714, 32715, 32716, 32717, 32718, 32719, 32720, 32721, 32722, 32723, 32724,
            32725, 32726, 32727, 32728, 32729, 32730, 32731, 32732, 32733, 32734, 32735, 32736,
            32737, 32738, 32739, 32740, 32741, 32742, 32743, 32744, 32745, 32746, 32747, 32748,
            32749, 32750, 32751, 32752, 32753, 32754, 32755, 32756, 32757, 32758, 32759, 32760
    };
}
