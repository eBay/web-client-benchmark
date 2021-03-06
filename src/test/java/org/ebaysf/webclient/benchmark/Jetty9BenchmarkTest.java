/*
 * Copyright (c) 2012-2013 eBay Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ebaysf.webclient.benchmark;

import static org.junit.Assert.assertTrue;

import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.junit.Test;

/**
 * @author Jason Brittain
 */
public class Jetty9BenchmarkTest extends AbstractBenchmarkTest {

    private HttpClient client;

    @Override
    protected void setup() {
        super.setup();
        client = new HttpClient() {
        		protected void doStop() throws Exception {
            }
        };
        client.setRequestBufferSize(8 * 1024);
        client.setResponseBufferSize(8 * 1024);
        client.setMaxRequestsQueuedPerDestination(50000);
        client.setStopTimeout(600000);
        client.setConnectTimeout(1000);
        try {
        		client.start();
        } catch (Exception e) {
        		assertTrue(false);
        }
    }

    @Override
    protected void tearDown() {
        super.tearDown();

        try {
            client.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testAsyncRequests() {
		String serverAsyncUrl = serverBaseUrl + serverAsyncUri;
		System.out.println(this.doBenchmark(serverAsyncUrl, "asyncWarmup", "runAsyncBatch"));
    }
    
    @Test
    public void testSyncRequests() {
		String serverSyncUrl = serverBaseUrl + serverSyncUri;
		System.out.println(this.doBenchmark(serverSyncUrl, "asyncWarmup", "runSyncBatch"));
    }

    @Test
    public void testAsyncLargeResponses() {
        String serverLargeUrl = serverBaseUrl + serverLargeUri;
		System.out.println(this.doBenchmark(serverLargeUrl, "asyncWarmup", "runAsyncBatch"));
    }

    @Test
    public void testSyncLargeResponses() {
        String serverLargeUrl = serverBaseUrl + serverLargeUri;
		System.out.println(this.doBenchmark(serverLargeUrl, "asyncWarmup", "runSyncBatch"));
    }
    
    public void asyncWarmup(final String testUrl) {
        for (int i = 0; i < this.warmupRequests; i++) {
        		try {
        			ContentResponse response = client.newRequest(testUrl).send();
        			response.getStatus();
        		} catch (Exception e) {}
        }
    }

    public BatchResult runAsyncBatch(final String testUrl) {
        final CountDownLatch latch = new CountDownLatch(this.threads);
        final Vector<ThreadResult> threadResults = new Vector<ThreadResult>(this.threads);

        long batchStart = System.nanoTime();
        for (int i = 0; i < this.threads; i++) {
            this.executor.submit(new Runnable() {

                public void run() {
                    final CountDownLatch responseReceivedLatch = new CountDownLatch(requestsPerThreadPerBatch);
					final AtomicInteger successful = new AtomicInteger();
					final long start = System.nanoTime();
					for (int i = 0; i < requestsPerThreadPerBatch; i++) {
						client.newRequest(testUrl).send(
							new BufferingResponseListener() {
								public void onComplete(org.eclipse.jetty.client.api.Result result) {
									if (result.isSucceeded()) {
										getContentAsString("UTF-8");
										successful.incrementAndGet();
									} else if (result.isFailed()) {
										System.out.println(result);
									}
									responseReceivedLatch.countDown();
								}
							}
						);
					}

					long totalTime = 0;
					try {
						responseReceivedLatch.await();
						totalTime = System.nanoTime() - start;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					threadResults.add(new ThreadResult(requestsPerThreadPerBatch, successful.get(), totalTime));
					latch.countDown();
				}
			});
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
        long batchTotalTime = System.nanoTime() - batchStart;

        return new BatchResult(threadResults, batchTotalTime);
    }
    
    public BatchResult runSyncBatch(final String testUrl) {
        final CountDownLatch latch = new CountDownLatch(this.threads);
        final Vector<ThreadResult> threadResults = new Vector<ThreadResult>(this.threads);

        long batchStart = System.nanoTime();
        for (int i = 0; i < this.threads; i++) {
            this.executor.submit(new Runnable() {

                public void run() {
                    final AtomicInteger successful = new AtomicInteger();
                    long start = System.nanoTime();
                    for (int i = 0; i < requestsPerThreadPerBatch; i++) {
						try {
							ContentResponse response = client.newRequest(testUrl).send();
							int status = response.getStatus();
							if (status == 200) {
								response.getContentAsString();
								successful.incrementAndGet();
							}
						} catch (Exception e) {
							// Failed request.. it doesn't get counted as successful.
						}

					}

                    long totalTime = System.nanoTime() - start;
                    threadResults.add(new ThreadResult(requestsPerThreadPerBatch, successful.get(), totalTime));
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
        long batchTotalTime = System.nanoTime() - batchStart;

        return new BatchResult(threadResults, batchTotalTime);
    }
}
