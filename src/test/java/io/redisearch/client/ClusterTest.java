package io.redisearch.client;

import io.redisearch.Document;
import io.redisearch.Query;
import io.redisearch.Schema;
import io.redisearch.SearchResult;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.*;


/**
 * Created by dvirsky on 09/02/17.
 */
public class ClusterTest {


    @Test
    public void search() throws Exception {
        ClusterClient cl = new ClusterClient("testung", "localhost", 7000);
        cl.broadcast("FLUSHDB");

        Schema sc = new Schema().addTextField("title", 1.0).addTextField("body", 1.0);

        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", "hello world");
        fields.put("body", "lorem ipsum");
        for (int i = 0; i < 100; i++) {
            assertTrue(cl.addDocument(String.format("doc%d", i), (double) i / 100.0, fields));
        }


        SearchResult res = cl.search(new Query("hello world").limit(0, 5).setWithScores());
        assertEquals(100, res.totalResults);
        assertEquals(5, res.docs.size());
        for (Document d : res.docs) {
            assertTrue(d.getId().startsWith("doc"));
            assertTrue(d.getScore() < 100);
            //System.out.println(d);
        }

        assertTrue(cl.deleteDocument("doc0"));
        assertFalse(cl.deleteDocument("doc0"));

        res = cl.search(new Query("hello world"));
        assertEquals(99, res.totalResults);

        assertTrue(cl.dropIndex());

            res = cl.search(new Query("hello world"));
        assertTrue(res.totalResults == 0);

    }

//
    @Test
    public void testNumericFilter() throws Exception {
        ClusterClient cl = new ClusterClient("testung", "localhost", 7000);
        cl.broadcast("FLUSHDB");

        Schema sc = new Schema().addTextField("title", 1.0).addNumericField("price");

        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", "hello world");

        for (int i = 0; i < 100; i++) {
            fields.put("price", i);
            assertTrue(cl.addDocument(String.format("doc%d", i), fields));
        }

        SearchResult res = cl.search(new Query("hello world").
                addFilter(new Query.NumericFilter("price", 0, 49)));

        assertEquals(50, res.totalResults);
        assertEquals(10, res.docs.size());
        for (Document d : res.docs) {
            long price = Long.valueOf((String) d.get("price"));
            assertTrue(price >= 0);
            assertTrue(price <= 49);
        }

        res = cl.search(new Query("hello world").
                addFilter(new Query.NumericFilter("price", 0, true, 49, true)));
        assertEquals(48, res.totalResults);
        assertEquals(10, res.docs.size());
        for (Document d : res.docs) {
            long price = Long.valueOf((String) d.get("price"));
            assertTrue(price > 0);
            assertTrue(price < 49);
        }
        res = cl.search(new Query("hello world").
                addFilter(new Query.NumericFilter("price", 50, 100)));
        assertEquals(50, res.totalResults);
        assertEquals(10, res.docs.size());
        for (Document d : res.docs) {
            long price = Long.valueOf((String) d.get("price"));
            assertTrue(price >= 50);
            assertTrue(price <= 100);
        }

        res = cl.search(new Query("hello world").
                addFilter(new Query.NumericFilter("price", 20, Double.POSITIVE_INFINITY)));
        assertEquals(80, res.totalResults);
        assertEquals(10, res.docs.size());

        res = cl.search(new Query("hello world").
                addFilter(new Query.NumericFilter("price", Double.NEGATIVE_INFINITY, 10)));
        assertEquals(11, res.totalResults);
        assertEquals(10, res.docs.size());

    }
//
    @Test
    public void testGeoFilter() throws Exception {
        ClusterClient cl = new ClusterClient("testung", "localhost", 7000);
        cl.broadcast("FLUSHDB");
        Schema sc = new Schema().addTextField("title", 1.0).addGeoField("loc");

        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", "hello world");
        fields.put("loc", "-0.441,51.458");
        assertTrue(cl.addDocument("doc1", fields));
        fields.put("loc", "-0.1,51.2");
        assertTrue(cl.addDocument("doc2", fields));

        SearchResult res = cl.search(new Query("hello world").
                addFilter(
                        new Query.GeoFilter("loc", -0.44, 51.45,
                                10, Query.GeoFilter.KILOMETERS)
                ));

        assertEquals(1, res.totalResults);
        res = cl.search(new Query("hello world").
                addFilter(
                        new Query.GeoFilter("loc", -0.44, 51.45,
                                100, Query.GeoFilter.KILOMETERS)
                ));
        assertEquals(2, res.totalResults);
    }
//
    @Test
    public void testPayloads() throws Exception {
        ClusterClient cl = new ClusterClient("testung", "localhost", 7000);
        cl.broadcast("FLUSHDB");

        Schema sc = new Schema().addTextField("title", 1.0);

        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));

        Map<String, Object> fields = new HashMap<>();
        fields.put("title", "hello world");
        String payload = "foo bar";
        assertTrue(cl.addDocument("doc1", 1.0, fields, false, false, payload.getBytes()));
        SearchResult res = cl.search(new Query("hello world").setWithPaload());
        assertEquals(1, res.totalResults);
        assertEquals(1, res.docs.size());

        assertEquals(payload, new String(res.docs.get(0).getPayload()));


    }

    @Test
    public void testQueryFlags() throws Exception {

        ClusterClient cl = new ClusterClient("testung", "localhost", 7000);
        cl.broadcast("FLUSHDB");

        Schema sc = new Schema().addTextField("title", 1.0);

        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));
        Map<String, Object> fields = new HashMap<>();


        for (int i = 0; i < 100; i++) {
            fields.put("title", i % 2 == 1 ? "hello worlds" : "hello world");
            assertTrue(cl.addDocument(String.format("doc%d", i), (double) i / 100.0, fields));
        }

        Query q = new Query("hello").setWithScores();
        SearchResult res = cl.search(q);

        assertEquals(100, res.totalResults);
        assertEquals(10, res.docs.size());

        for (Document d : res.docs) {
            assertTrue(d.getId().startsWith("doc"));
            assertTrue(d.getScore() != 1.0);
            assertTrue(((String) d.get("title")).startsWith("hello world"));
        }

        q = new Query("hello").setNoContent();
        res = cl.search(q);
        for (Document d : res.docs) {
            assertTrue(d.getId().startsWith("doc"));
            assertTrue(d.getScore() == 1.0);
            assertEquals(null, d.get("title"));
        }

        // test verbatim vs. stemming
        res = cl.search(new Query("hello worlds"));
        assertEquals(100, res.totalResults);
        res = cl.search(new Query("hello worlds").setVerbatim());
        assertEquals(50, res.totalResults);

        res = cl.search(new Query("hello a world").setVerbatim());
        assertEquals(100, res.totalResults);
        res = cl.search(new Query("hello a world").setVerbatim().setNoStopwords());
        assertEquals(0, res.totalResults);
    }
//
//    @Test
//    public void testAddHash() throws Exception {
//        Client cl = new Client("testung", "localhost", 6379);
//        Jedis conn = cl._conn();
//        conn.flushDB();
//        Schema sc = new Schema().addTextField("title", 1.0);
//        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));
//        HashMap hm = new HashMap();
//        hm.put("title", "hello world");
//        conn.hmset("foo", hm);
//
//        assertTrue(cl.addHash("foo", 1, false));
//        SearchResult res = cl.search(new Query("hello world").setVerbatim());
//        assertEquals(1, res.totalResults);
//        assertEquals("foo", res.docs.get(0).getId());
//
//    }
//
//    @Test
//    public void testDrop() throws Exception {
//        Client cl = new Client("testung", "localhost", 6379);
//        cl._conn().flushDB();
//
//        Schema sc = new Schema().addTextField("title", 1.0);
//
//        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));
//        Map<String, Object> fields = new HashMap<>();
//        fields.put("title", "hello world");
//        for (int i = 0; i < 100; i++) {
//            assertTrue(cl.addDocument(String.format("doc%d", i), fields));
//        }
//
//
//        SearchResult res = cl.search(new Query("hello world"));
//        assertEquals(100, res.totalResults);
//
//        assertTrue(cl.dropIndex());
//
//        Jedis conn = cl._conn();
//
//        Set<String> keys = conn.keys("*");
//        assertTrue(keys.isEmpty());
//
//    }
//
//
//    @Test
//    public void testInfo() throws Exception {
//        Client cl = new Client("testung", "localhost", 6379);
//        cl._conn().flushDB();
//
//        Schema sc = new Schema().addTextField("title", 1.0);
//        assertTrue(cl.createIndex(sc, Client.IndexOptions.Default()));
//
//        Map<String, Object> info = cl.getInfo();
//        assertEquals("testung", info.get("index_name"));
//
//    }
}