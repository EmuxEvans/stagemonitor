package org.stagemonitor.requestmonitor;

import static com.codahale.metrics.MetricRegistry.name;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.stagemonitor.core.Stagemonitor.getMetricRegistry;
import static org.stagemonitor.core.util.GraphiteSanitizer.sanitizeGraphiteMetricSegment;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.stagemonitor.core.Stagemonitor;
import org.stagemonitor.web.monitor.HttpRequestTrace;
import org.stagemonitor.web.monitor.MonitoredHttpRequest;
import org.stagemonitor.web.monitor.filter.StatusExposingByteCountingServletResponse;

public class MonitoredHttpExecutionForwardingTest {

	private RequestMonitor.RequestInformation<HttpRequestTrace> requestInformation1;
	private RequestMonitor.RequestInformation<HttpRequestTrace> requestInformation2;
	private RequestMonitor.RequestInformation<HttpRequestTrace> requestInformation3;

	@Before
	public void clearState() {
		getMetricRegistry().removeMatching(new MetricFilter() {
			@Override
			public boolean matches(String name, Metric metric) {
				return true;
			}
		});
		requestInformation1 = requestInformation2 = requestInformation3 = null;
	}

	@Test
	public void testForwarding() throws Exception {
		TestObject testObject = new TestObject(new RequestMonitor());
		testObject.monitored1();
		assertFalse(requestInformation1.isForwarded());
		assertTrue(requestInformation2.isForwarded());
		assertTrue(requestInformation3.isForwarded());

		assertEquals("/monitored3", requestInformation3.requestTrace.getUrl());
		assertEquals("GET /monitored3", requestInformation3.requestTrace.getName());

		assertNull(getMetricRegistry().getTimers().get(name("request", sanitizeGraphiteMetricSegment("GET /monitored1"), "server", "time", "total" )));
		assertNull(getMetricRegistry().getTimers().get(name("request", sanitizeGraphiteMetricSegment("GET /monitored2"), "server", "time", "total" )));
		assertNotNull(getMetricRegistry().getTimers().get(name("request", sanitizeGraphiteMetricSegment("GET /monitored3"), "server", "time", "total" )));
	}

	@Test
	public void testNoForwarding() throws Exception {
		TestObject testObject = new TestObject(new RequestMonitor());
		testObject.monitored3();
		assertNull(requestInformation1);
		assertNull(requestInformation2);
		assertFalse(requestInformation3.isForwarded());

		assertEquals("/monitored3", requestInformation3.requestTrace.getUrl());
		assertEquals("GET /monitored3", requestInformation3.requestTrace.getName());

		assertNull(getMetricRegistry().getTimers().get(name("request", sanitizeGraphiteMetricSegment("GET /monitored1"), "server", "time", "total" )));
		assertNull(getMetricRegistry().getTimers().get(name("request", sanitizeGraphiteMetricSegment("GET /monitored2"), "server", "time", "total" )));
		assertNotNull(getMetricRegistry().getTimers().get(name("request", sanitizeGraphiteMetricSegment("GET /monitored3"), "server", "time", "total" )));
	}


	private class TestObject {
		private final RequestMonitor requestMonitor;

		private TestObject(RequestMonitor requestMonitor) {
			this.requestMonitor = requestMonitor;
		}

		private void monitored1() throws Exception {
			requestInformation1 = requestMonitor.monitor(new MonitoredHttpRequest(new MockHttpServletRequest("GET", "/monitored1"),
					new StatusExposingByteCountingServletResponse(new MockHttpServletResponse()),
					new FilterChain() {
						@Override
						public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
							try {
								monitored2();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}, Stagemonitor.getConfiguration()));
		}

		private void monitored2() throws Exception {
			requestInformation2 = requestMonitor.monitor(new MonitoredHttpRequest(new MockHttpServletRequest("GET", "/monitored2"),
					new StatusExposingByteCountingServletResponse(new MockHttpServletResponse()),
					new FilterChain() {
						@Override
						public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
							try {
								monitored3();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}, Stagemonitor.getConfiguration()));
		}

		private void monitored3() throws Exception {
			requestInformation3 = requestMonitor.monitor(new MonitoredHttpRequest(new MockHttpServletRequest("GET", "/monitored3"),
					new StatusExposingByteCountingServletResponse(new MockHttpServletResponse()),
					new FilterChain() {
						@Override
						public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
							// actual work
						}
					}, Stagemonitor.getConfiguration()));
		}

	}

}
