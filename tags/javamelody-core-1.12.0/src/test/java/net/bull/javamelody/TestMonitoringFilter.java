/*
 * Copyright 2008-2009 by Emeric Vernat, Bull
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody;

import static net.bull.javamelody.HttpParameters.ACTION_PARAMETER;
import static net.bull.javamelody.HttpParameters.COLLECTOR_PARAMETER;
import static net.bull.javamelody.HttpParameters.CURRENT_REQUESTS_PART;
import static net.bull.javamelody.HttpParameters.DATABASE_PART;
import static net.bull.javamelody.HttpParameters.PART_PARAMETER;
import static net.bull.javamelody.HttpParameters.POM_XML_PART;
import static net.bull.javamelody.HttpParameters.PROCESSES_PART;
import static net.bull.javamelody.HttpParameters.REQUEST_PARAMETER;
import static net.bull.javamelody.HttpParameters.SESSIONS_PART;
import static net.bull.javamelody.HttpParameters.SESSION_ID_PARAMETER;
import static net.bull.javamelody.HttpParameters.USAGES_PART;
import static net.bull.javamelody.HttpParameters.WEB_XML_PART;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.ehcache.CacheManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test unitaire de la classe MonitoringFilter.
 * @author Emeric Vernat
 */
public class TestMonitoringFilter {
	private FilterConfig config;
	private ServletContext context;
	private MonitoringFilter monitoringFilter;

	/**
	 * Initialisation.
	 */
	@Before
	public void setUp() {
		tearDown();
		config = createNiceMock(FilterConfig.class);
		context = createNiceMock(ServletContext.class);
		expect(config.getServletContext()).andReturn(context).anyTimes();
		// anyTimes sur getInitParameter car TestJdbcDriver a pu fixer la propriété système à false
		expect(
				context.getInitParameter(Parameters.PARAMETER_SYSTEM_PREFIX
						+ Parameter.DISABLED.getCode())).andReturn(null).anyTimes();
		expect(config.getInitParameter(Parameter.DISABLED.getCode())).andReturn(null).anyTimes();
		expect(context.getMajorVersion()).andReturn(2).anyTimes();
		expect(context.getMinorVersion()).andReturn(5).anyTimes();
		expect(context.getServerInfo()).andReturn("EasyMock").anyTimes();
		expect(context.getContextPath()).andReturn("/test").anyTimes();
		monitoringFilter = new MonitoringFilter();
	}

	/**
	 * Finalisation.
	 */
	@After
	public void tearDown() {
		destroy();
	}

	private void destroy() {
		// on désactive le stop sur le timer JRobin car sinon les tests suivants ne fonctionneront
		// plus si ils utilisent JRobin
		System.setProperty(Parameters.PARAMETER_SYSTEM_PREFIX + "jrobinStopDisabled", "true");
		if (monitoringFilter != null) {
			monitoringFilter.destroy();
		}
	}

	/** Test.
	 * @throws ServletException e */
	@Test
	public void testInit() throws ServletException {
		try {
			init();
			setUp();
			expect(config.getInitParameter(Parameter.DISPLAYED_COUNTERS.getCode())).andReturn(
					"http,sql").anyTimes();
			expect(config.getInitParameter(Parameter.HTTP_TRANSFORM_PATTERN.getCode())).andReturn(
					"[0-9]").anyTimes();
			init();
			setUp();
			expect(config.getInitParameter(Parameter.URL_EXCLUDE_PATTERN.getCode())).andReturn(
					"/static/*").anyTimes();
			init();
			setUp();
			expect(config.getInitParameter(Parameter.ALLOWED_ADDR_PATTERN.getCode())).andReturn(
					"127\\.0\\.0\\.1").anyTimes();
			init();
		} finally {
			destroy();
		}
	}

	private void init() throws ServletException {
		replay(config);
		replay(context);
		monitoringFilter.init(config);
		verify(config);
		verify(context);
	}

	/** Test.
	 * @throws ServletException e */
	@Test
	public void testLog() throws ServletException {
		try {
			final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
			expect(request.getRemoteAddr()).andReturn("127.0.0.1");
			expect(request.getRequestURI()).andReturn("/test/request");
			expect(request.getContextPath()).andReturn("/test");
			expect(request.getQueryString()).andReturn("param1=1");
			expect(request.getMethod()).andReturn("GET");

			replay(config);
			replay(context);
			monitoringFilter.init(config);
			monitoringFilter.log(request, "test", 1000, false, 10000);
			verify(config);
			verify(context);
		} finally {
			destroy();
		}
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoFilter() throws ServletException, IOException {
		try {
			final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
			expect(request.getRequestURI()).andReturn("/test/request").anyTimes();
			expect(request.getContextPath()).andReturn("/test").anyTimes();
			expect(request.getQueryString()).andReturn("param1=1");
			expect(request.getMethod()).andReturn("GET");
			final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
			final FilterChain chain = createNiceMock(FilterChain.class);

			replay(config);
			replay(context);
			replay(request);
			replay(response);
			replay(chain);
			monitoringFilter.init(config);
			monitoringFilter.doFilter(request, response, chain);
			verify(config);
			verify(context);
			verify(request);
			verify(response);
			verify(chain);
		} finally {
			destroy();
		}
	}

	/** Test.
	 * @throws IOException e */
	@Test
	public void testFilterServletResponseWrapper() throws IOException {
		try {
			final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
			expect(response.getOutputStream()).andReturn(
					new FilterServletOutputStream(new ByteArrayOutputStream())).anyTimes();
			expect(response.getCharacterEncoding()).andReturn("ISO-8859-1").anyTimes();
			final CounterServletResponseWrapper wrappedResponse = new CounterServletResponseWrapper(
					response);
			replay(response);
			assertNotNull("getOutputStream", wrappedResponse.getOutputStream());
			assertNotNull("getOutputStream bis", wrappedResponse.getOutputStream());
			assertNotNull("getOutputStream", wrappedResponse.getCharacterEncoding());
			wrappedResponse.close();
			verify(response);

			final HttpServletResponse response2 = createNiceMock(HttpServletResponse.class);
			expect(response2.getOutputStream()).andReturn(
					new FilterServletOutputStream(new ByteArrayOutputStream())).anyTimes();
			expect(response2.getCharacterEncoding()).andReturn(null).anyTimes();
			final CounterServletResponseWrapper wrappedResponse2 = new CounterServletResponseWrapper(
					response);
			replay(response2);
			assertNotNull("getWriter", wrappedResponse2.getWriter());
			assertNotNull("getWriter bis", wrappedResponse2.getWriter());
			wrappedResponse2.close();
			verify(response2);
		} finally {
			destroy();
		}
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoring() throws ServletException, IOException {
		monitoring(Collections.<String, String> emptyMap());
		monitoring(Collections.<String, String> singletonMap("format", "html"));
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithPeriod() throws ServletException, IOException {
		monitoring(Collections.<String, String> singletonMap("period", "jour"));
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithResource() throws ServletException, IOException {
		monitoring(Collections.<String, String> singletonMap("resource", "monitoring.css"));
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithGraph() throws ServletException, IOException {
		final Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("graph", "usedMemory");
		parameters.put("width", "800");
		parameters.put("height", "600");
		monitoring(parameters);
		parameters.put("graph", "unknown");
		monitoring(parameters, false);
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithParts() throws ServletException, IOException {
		final Map<String, String> parameters = new HashMap<String, String>();

		parameters.put(PART_PARAMETER, CURRENT_REQUESTS_PART);
		monitoring(parameters);

		setProperty(Parameter.SYSTEM_ACTIONS_ENABLED, "true");
		parameters.put(PART_PARAMETER, PROCESSES_PART);
		monitoring(parameters);
		parameters.put(PART_PARAMETER, DATABASE_PART);
		monitoring(parameters);
		parameters.put(REQUEST_PARAMETER, "0");
		monitoring(parameters);
		parameters.remove(REQUEST_PARAMETER);
		// il ne faut pas faire un heapHisto sans thread comme dans TestHtmlHeapHistogramReport
		//		parameters.put(PART_PARAMETER, HEAP_HISTO_PART);
		//		monitoring(parameters);
		parameters.put(PART_PARAMETER, SESSIONS_PART);
		monitoring(parameters);
		parameters.put(PART_PARAMETER, SESSIONS_PART);
		parameters.put(SESSION_ID_PARAMETER, "expired session");
		monitoring(parameters);
		parameters.remove(SESSION_ID_PARAMETER);
		parameters.put(PART_PARAMETER, WEB_XML_PART);
		monitoring(parameters, false);
		parameters.put(PART_PARAMETER, POM_XML_PART);
		monitoring(parameters, false);

		parameters.put(PART_PARAMETER, "graph");
		parameters.put("graph", "usedMemory");
		monitoring(parameters);

		parameters.put(PART_PARAMETER, USAGES_PART);
		parameters.put("graph", "unknown");
		monitoring(parameters);

		parameters.put(PART_PARAMETER, "unknown part");
		boolean exception = false;
		try {
			monitoring(parameters);
		} catch (final IllegalArgumentException e) {
			exception = true;
		}
		assertTrue("exception if unknown part", exception);
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithActions() throws ServletException, IOException {
		final Map<String, String> parameters = new HashMap<String, String>();

		setProperty(Parameter.SYSTEM_ACTIONS_ENABLED, "true");
		parameters.put(ACTION_PARAMETER, Action.GC.toString());
		monitoring(parameters);
		parameters.put(ACTION_PARAMETER, Action.INVALIDATE_SESSIONS.toString());
		monitoring(parameters);
		parameters.put(ACTION_PARAMETER, Action.INVALIDATE_SESSION.toString());
		parameters.put(SESSION_ID_PARAMETER, "123456789");
		monitoring(parameters);
		parameters.put(ACTION_PARAMETER, Action.CLEAR_CACHES.toString());
		monitoring(parameters);
		if (CacheManager.getInstance().getCache("test clear") == null) {
			CacheManager.getInstance().addCache("test clear");
		}
		monitoring(parameters);
		parameters.put(ACTION_PARAMETER, Action.CLEAR_COUNTER.toString());
		parameters.put("counter", "all");
		monitoring(parameters);
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithFormatPdf() throws ServletException, IOException {
		monitoring(Collections.<String, String> singletonMap("format", "pdf"));
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithFormatSerialized() throws ServletException, IOException {
		setProperty(Parameter.SYSTEM_ACTIONS_ENABLED, "true");
		final Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("format", TransportFormat.SERIALIZED.getCode());
		monitoring(parameters);
		parameters.put(PART_PARAMETER, SESSIONS_PART);
		monitoring(parameters);
		parameters.put(PART_PARAMETER, SESSIONS_PART);
		parameters.put(SESSION_ID_PARAMETER, "expired session");
		monitoring(parameters);
		parameters.remove(SESSION_ID_PARAMETER);
		parameters.put(PART_PARAMETER, PROCESSES_PART);
		monitoring(parameters);
		parameters.put(PART_PARAMETER, DATABASE_PART);
		monitoring(parameters);
		parameters.put(REQUEST_PARAMETER, "0");
		monitoring(parameters);
		parameters.remove(REQUEST_PARAMETER);
		// il ne faut pas faire un heapHisto sans thread comme dans TestHtmlHeapHistogramReport
		//		parameters.put(PART_PARAMETER, HEAP_HISTO_PART);
		//		monitoring(parameters);
		parameters.put(PART_PARAMETER, null);
		parameters.put("format", TransportFormat.SERIALIZED.getCode());
		parameters.put(COLLECTOR_PARAMETER, "stop");
		monitoring(parameters);
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithFormatXml() throws ServletException, IOException {
		setProperty(Parameter.SYSTEM_ACTIONS_ENABLED, "true");
		final Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("format", TransportFormat.XML.getCode());
		monitoring(parameters);
		parameters.put(PART_PARAMETER, SESSIONS_PART);
		monitoring(parameters);
		parameters.put(PART_PARAMETER, PROCESSES_PART);
		monitoring(parameters);
		parameters.put(PART_PARAMETER, DATABASE_PART);
		monitoring(parameters);
		// il ne faut pas faire un heapHisto sans thread comme dans TestHtmlHeapHistogramReport
		//		parameters.put(PART_PARAMETER, HEAP_HISTO_PART);
		//		monitoring(parameters);
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testDoMonitoringWithFormatJson() throws ServletException, IOException {
		setProperty(Parameter.SYSTEM_ACTIONS_ENABLED, "true");
		final Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("format", TransportFormat.JSON.getCode());
		monitoring(parameters);
		parameters.put(PART_PARAMETER, SESSIONS_PART);
		monitoring(parameters);
		parameters.put(PART_PARAMETER, PROCESSES_PART);
		monitoring(parameters);
		// il ne faut pas faire un heapHisto sans thread comme dans TestHtmlHeapHistogramReport
		//		parameters.put(PART_PARAMETER, HEAP_HISTO_PART);
		//		monitoring(parameters);
	}

	private void monitoring(Map<String, String> parameters) throws IOException, ServletException {
		monitoring(parameters, true);
	}

	private void monitoring(Map<String, String> parameters, boolean checkResultContent)
			throws IOException, ServletException {
		setUp();

		try {
			final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
			expect(request.getRequestURI()).andReturn("/test/monitoring").anyTimes();
			expect(request.getContextPath()).andReturn("/test").anyTimes();
			final Random random = new Random();
			if (random.nextBoolean()) {
				expect(request.getHeaders("Accept-Encoding")).andReturn(
						Collections.enumeration(Arrays.asList("application/gzip"))).anyTimes();
			} else {
				expect(request.getHeaders("Accept-Encoding")).andReturn(
						Collections.enumeration(Arrays.asList("text/html"))).anyTimes();
			}
			for (final Map.Entry<String, String> entry : parameters.entrySet()) {
				expect(request.getParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
			}
			final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			expect(response.getOutputStream()).andReturn(new FilterServletOutputStream(output))
					.anyTimes();
			final StringWriter stringWriter = new StringWriter();
			expect(response.getWriter()).andReturn(new PrintWriter(stringWriter)).anyTimes();
			final FilterChain chain = createNiceMock(FilterChain.class);

			replay(config);
			replay(context);
			replay(request);
			replay(response);
			replay(chain);
			monitoringFilter.init(config);
			monitoringFilter.doFilter(request, response, chain);
			verify(config);
			verify(context);
			verify(request);
			verify(response);
			verify(chain);

			if (checkResultContent) {
				assertTrue("result", output.size() != 0 || stringWriter.getBuffer().length() != 0);
			}
		} finally {
			destroy();
		}
	}

	/** Test.
	 * @throws ServletException e */
	@Test
	public void testWriteHtmlToLastShutdownFile() throws ServletException {
		try {
			replay(config);
			replay(context);
			monitoringFilter.init(config);
			final Timer timer = new Timer("test timer", true);
			try {
				final Counter sqlCounter = new Counter("sql", "db.png");
				final Collector collector = new Collector("test", Arrays.asList(sqlCounter), timer);
				timer.cancel();
				new MonitoringController(collector, null).writeHtmlToLastShutdownFile();
				verify(config);
				verify(context);
			} finally {
				timer.cancel();
			}
		} finally {
			destroy();
		}
	}

	private static void setProperty(Parameter parameter, String value) {
		System.setProperty(Parameters.PARAMETER_SYSTEM_PREFIX + parameter.getCode(), value);
	}
}