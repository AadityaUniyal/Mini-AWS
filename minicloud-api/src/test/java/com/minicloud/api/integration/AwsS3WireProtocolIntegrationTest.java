package com.minicloud.api.integration;

import com.minicloud.api.config.TestSecurityConfig;
import com.minicloud.api.domain.Bucket;
import com.minicloud.api.domain.BucketRepository;
import com.minicloud.api.domain.ObjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "java.awt.headless=true",
    "minicloud.system-tray.enabled=false",
    "minicloud.h2.tcp.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:testdb_s3_wire",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=test"
})
@Import(TestSecurityConfig.class)
public class AwsS3WireProtocolIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private ObjectRepository objectRepository;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/s3";
    }

    @BeforeEach
    void setUp() {
        objectRepository.deleteAll();
        bucketRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        objectRepository.deleteAll();
        bucketRepository.deleteAll();
    }

    @Test
    @DisplayName("AWS S3 Wire Protocol: Full bucket and object lifecycle with standard S3 XML")
    void testAwsS3WireProtocolLifecycle() {
        String bucketName = "test-wire-bucket-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Bucket via PUT /s3/{bucket}
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> createReq = new HttpEntity<>(headers);
        ResponseEntity<Void> createResp = restTemplate.exchange(
                getBaseUrl() + "/" + bucketName,
                HttpMethod.PUT,
                createReq,
                Void.class
        );
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertTrue(bucketRepository.findByName(bucketName).isPresent());

        // 2. List All Buckets via GET /s3 (returns XML)
        ResponseEntity<String> listBucketsResp = restTemplate.getForEntity(getBaseUrl(), String.class);
        assertEquals(HttpStatus.OK, listBucketsResp.getStatusCode());
        assertNotNull(listBucketsResp.getBody());
        assertTrue(listBucketsResp.getBody().contains("<ListAllMyBucketsResult"));
        assertTrue(listBucketsResp.getBody().contains("<Name>" + bucketName + "</Name>"));

        // 3. Put Object via PUT /s3/{bucket}/{key}
        String key = "documents/hello.txt";
        byte[] payload = "Hello MiniCloud Wire Protocol!".getBytes();
        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<byte[]> putReq = new HttpEntity<>(payload, putHeaders);

        ResponseEntity<Void> putResp = restTemplate.exchange(
                getBaseUrl() + "/" + bucketName + "/" + key,
                HttpMethod.PUT,
                putReq,
                Void.class
        );
        assertEquals(HttpStatus.OK, putResp.getStatusCode());
        String etag = putResp.getHeaders().getFirst("ETag");
        assertNotNull(etag);
        assertTrue(etag.length() > 0);

        // 4. List Objects via GET /s3/{bucket} (returns XML)
        ResponseEntity<String> listObjResp = restTemplate.getForEntity(
                getBaseUrl() + "/" + bucketName,
                String.class
        );
        assertEquals(HttpStatus.OK, listObjResp.getStatusCode());
        assertNotNull(listObjResp.getBody());
        assertTrue(listObjResp.getBody().contains("<ListBucketResult"));
        assertTrue(listObjResp.getBody().contains("<Key>" + key + "</Key>"));
        assertTrue(listObjResp.getBody().contains("<Size>" + payload.length + "</Size>"));

        // 5. Get Object via GET /s3/{bucket}/{key}
        ResponseEntity<byte[]> getObjResp = restTemplate.getForEntity(
                getBaseUrl() + "/" + bucketName + "/" + key,
                byte[].class
        );
        assertEquals(HttpStatus.OK, getObjResp.getStatusCode());
        assertArrayEquals(payload, getObjResp.getBody());
        assertEquals(etag, getObjResp.getHeaders().getFirst("ETag"));

        // 6. Delete Object via DELETE /s3/{bucket}/{key}
        ResponseEntity<Void> delObjResp = restTemplate.exchange(
                getBaseUrl() + "/" + bucketName + "/" + key,
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, delObjResp.getStatusCode());

        // Verify object deleted
        ResponseEntity<byte[]> getDeletedResp = restTemplate.getForEntity(
                getBaseUrl() + "/" + bucketName + "/" + key,
                byte[].class
        );
        assertEquals(HttpStatus.NOT_FOUND, getDeletedResp.getStatusCode());

        // 7. Delete Bucket via DELETE /s3/{bucket}
        ResponseEntity<Void> delBucketResp = restTemplate.exchange(
                getBaseUrl() + "/" + bucketName,
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, delBucketResp.getStatusCode());
        assertTrue(bucketRepository.findByName(bucketName).isEmpty());
    }

    @Test
    @DisplayName("AWS S3 Wire Protocol: RFC 7233 byte-range partial content streaming (HTTP 206)")
    void testAwsS3ByteRangeStreaming() {
        String bucketName = "range-bucket-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Bucket
        restTemplate.exchange(getBaseUrl() + "/" + bucketName, HttpMethod.PUT, new HttpEntity<>(new HttpHeaders()), Void.class);

        // 2. Put Object with 16 bytes: "0123456789ABCDEF"
        String key = "range-test.dat";
        byte[] payload = "0123456789ABCDEF".getBytes();
        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        restTemplate.exchange(getBaseUrl() + "/" + bucketName + "/" + key, HttpMethod.PUT, new HttpEntity<>(payload, putHeaders), Void.class);

        // 3. Request bytes 0-9
        HttpHeaders rangeHeaders1 = new HttpHeaders();
        rangeHeaders1.set("Range", "bytes=0-9");
        ResponseEntity<byte[]> rangeResp1 = restTemplate.exchange(
                getBaseUrl() + "/" + bucketName + "/" + key,
                HttpMethod.GET,
                new HttpEntity<>(rangeHeaders1),
                byte[].class
        );
        assertEquals(HttpStatus.PARTIAL_CONTENT, rangeResp1.getStatusCode());
        assertEquals("bytes 0-9/16", rangeResp1.getHeaders().getFirst("Content-Range"));
        assertEquals(10, rangeResp1.getBody().length);
        assertEquals("0123456789", new String(rangeResp1.getBody()));

        // 4. Request bytes 10-15
        HttpHeaders rangeHeaders2 = new HttpHeaders();
        rangeHeaders2.set("Range", "bytes=10-15");
        ResponseEntity<byte[]> rangeResp2 = restTemplate.exchange(
                getBaseUrl() + "/" + bucketName + "/" + key,
                HttpMethod.GET,
                new HttpEntity<>(rangeHeaders2),
                byte[].class
        );
        assertEquals(HttpStatus.PARTIAL_CONTENT, rangeResp2.getStatusCode());
        assertEquals("bytes 10-15/16", rangeResp2.getHeaders().getFirst("Content-Range"));
        assertEquals(6, rangeResp2.getBody().length);
        assertEquals("ABCDEF", new String(rangeResp2.getBody()));
    }
}
