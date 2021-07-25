package io.github.zzw6776.filter;

import lombok.extern.log4j.Log4j2;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 请求转发Filter
 * @author ZZW
 */
@Log4j2
@ConditionalOnProperty(value = "httpForward.switch", havingValue = "true")
public class HttpForwardFilter implements Filter {


    /**
     * httpclient参数
     */
    @Value("${httpForward.httpclient.doHandleRedirects:false}")
    public boolean doHandleRedirects ;
    @Value("${httpForward.httpclient.connectTimeout:-1}")
    public int connectTimeout ;
    @Value("${httpForward.httpclient.readTimeout:-1}")
    public int readTimeout ;
    @Value("${httpForward.httpclient.connectionRequestTimeout:-1}")
    public int connectionRequestTimeout ;

    @Value("${httpForward.httpclient.maxConnections:-1}")
    public int maxConnections ;

    /**
     * 是否转发#号后面参数
     */
    @Value("${httpForward.doSendUrlFragment:true}")
    public boolean doSendUrlFragment = true;
    /**
     * 是否保存host
     * 若为true则保持入参的host
     */
    @Value("${httpForward.doPreserveHost:false}")
    public boolean doPreserveHost;
    /**
     * 是否保存cookie
     * 若为true会保存转发后的cookie
     */
    @Value("${httpForward.doPreserveCookies:false}")
    public boolean doPreserveCookies ;

    @Value("${httpForward.doForwardIP:true}")
    public boolean doForwardIP ;


    String forHeaderName = "X-Forwarded-For";

    String protoHeaderName = "X-Forwarded-Proto";

    /**
     * Copy a request header from the servlet client to the forward request.
     * This is easily overridden to filter out certain headers if desired.
     */
    private static final HeaderGroup hopByHopHeaders;


    @Autowired
    IHttpForwardUtil httpForwardUtil;

    private static final BitSet asciiQueryChars;

    static {
        char[] c_unreserved = "_-!.~'()*".toCharArray();//plus alphanum
        char[] c_punct = ",;:$&+=".toCharArray();
        char[] c_reserved = "?/[]@".toCharArray();//plus punct

        asciiQueryChars = new BitSet(128);
        for (char c = 'a'; c <= 'z'; c++) asciiQueryChars.set((int) c);
        for (char c = 'A'; c <= 'Z'; c++) asciiQueryChars.set((int) c);
        for (char c = '0'; c <= '9'; c++) asciiQueryChars.set((int) c);
        for (char c : c_unreserved) asciiQueryChars.set((int) c);
        for (char c : c_punct) asciiQueryChars.set((int) c);
        for (char c : c_reserved) asciiQueryChars.set((int) c);

        asciiQueryChars.set((int) '%');//leave existing percent escapes in place
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("init HttpForwardFilter");

        httpRequestConfig = RequestConfig.custom()
                .setRedirectsEnabled(doHandleRedirects)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .build();
        httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(httpRequestConfig)
                .setDefaultSocketConfig(SocketConfig.DEFAULT)
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(30)
                .build();
    }

    @Override
    public void destroy() {
        log.info("destroy HttpForwardFilter");
    }


    /**
     * httpclient请求配置
     */
    private RequestConfig httpRequestConfig;

    /**
     * httpclient请求客户端
     */
    private HttpClient httpClient;


    private Map<String, String> getHeadersInfo(HttpServletRequest request) {

        Map<String, String> map = new HashMap<String, String>();

        Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = (String) headerNames.nextElement();
            String value = request.getHeader(key);
            map.put(key, value);
        }

        return map;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        ContentCachingRequestWrapper reusableHttpServletRequest = new ContentCachingRequestWrapper((HttpServletRequest) request);
        String uri = reusableHttpServletRequest.getRequestURI();
        String method = reusableHttpServletRequest.getMethod();
        String queryString = reusableHttpServletRequest.getQueryString();
        Map<String, String> headersInfo = getHeadersInfo(reusableHttpServletRequest);
        String contentType = request.getContentType();
        String scheme = reusableHttpServletRequest.getScheme();
        String serverName = reusableHttpServletRequest.getServerName();
        int serverPort = reusableHttpServletRequest.getServerPort();


        //防止重复转发
        List<String> headerKeysLowCase = headersInfo.keySet().stream().map(key -> key.toLowerCase()).collect(Collectors.toList());
        if (headerKeysLowCase.contains(forHeaderName.toLowerCase()) || headerKeysLowCase.contains(protoHeaderName.toLowerCase())) {
            chain.doFilter(reusableHttpServletRequest, response);
            return;
        }

        Boolean isForward = httpForwardUtil.isForward(scheme, serverName, serverPort,uri,headersInfo,method, contentType);

        if (!isForward) {
            chain.doFilter(reusableHttpServletRequest, response);
            return;
        }

        URI targetUri = httpForwardUtil.getTargetUri();

        try {
            // 因为不确定是否真的兼容，所以不传输协议版本
            String forwardToUri = rewriteUrlFromRequest(reusableHttpServletRequest,targetUri);
            // 初始化一个转发请求对象HttpRequest
            HttpRequest forwardRequest = initForwardRequest(reusableHttpServletRequest, forwardToUri);
            // 拷贝header
            copyHeaders(reusableHttpServletRequest, forwardRequest);

            // 设置header: X-Forwarded-Proto
            setXForwardedForHeader(reusableHttpServletRequest, forwardRequest);


            //设置自定义header
            addCustomerHeader(forwardRequest,httpForwardUtil.getCustomerHeaders());

            HttpServletResponse servletResponse = (HttpServletResponse) response;

            HttpResponse proxyResponse = null;

            try {
                // 执行转发请求
                proxyResponse = httpClient.execute(URIUtils.extractHost(targetUri), forwardRequest);
                // 拷贝statusCode
                servletResponse.setStatus(proxyResponse.getStatusLine().getStatusCode(), proxyResponse.getStatusLine().getReasonPhrase());
                // 拷贝header
                copyResponseHeaders(proxyResponse, reusableHttpServletRequest, servletResponse);
                // 处理响应entity
                if (Objects.equals(HttpServletResponse.SC_NOT_MODIFIED, proxyResponse.getStatusLine().getStatusCode())) {
                    // 304状态码不发送body entity/content
                    servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
                } else {
                    copyResponseEntity(proxyResponse, servletResponse);
                }
            } catch (Exception e) {
                handleRequestException(forwardRequest, e);
            } finally {
                // 确保entire entity was consumed, so the connection is released
                // servlet outputStream不需要手动close: http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
                if (proxyResponse != null) {
                    EntityUtils.consumeQuietly(proxyResponse.getEntity());
                }
            }

        }finally {
         // 解析request中的参数
            //form-date的不能获取body里的参数
        String body = new String(reusableHttpServletRequest.getContentAsByteArray());
        log.info("是否转发:[{}], Method:[{}], URI:[{}], toService:[{}], query:[{}], body:[{}], headers:[{}]", isForward,  method, uri , targetUri.getHost(), queryString, body, headersInfo.toString() );

        }

    }

    private void addCustomerHeader(HttpRequest forwardRequest,List<Header> customerHeaders) {
        for (Header customerHeader : customerHeaders) {
            forwardRequest.addHeader(customerHeader);
        }
    }

    /**
     * 处理请求异常
     *
     * @param proxyRequest
     * @param e
     * @throws ServletException
     * @throws IOException
     */
    private static void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
        //abort request, according to best practice with HttpClient
        if (proxyRequest instanceof AbortableHttpRequest) {
            AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
            abortableHttpRequest.abort();
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;

        }
        if (e instanceof ServletException) {
            throw (ServletException) e;
        }
        //noinspection ConstantConditions
        if (e instanceof IOException) {
            throw (IOException) e;
        }
        throw new RuntimeException(e);
    }

    /**
     * 拷贝response的entity
     *
     * @param forwardResponse
     * @param servletResponse
     * @throws IOException
     */
    private static void copyResponseEntity(HttpResponse forwardResponse, HttpServletResponse servletResponse) throws IOException {
        HttpEntity entity = forwardResponse.getEntity();
        if (entity != null) {
            OutputStream servletOutputStream = servletResponse.getOutputStream();
            entity.writeTo(servletOutputStream);
        }
    }

    /**
     * 拷贝response的header
     *
     * @param proxyResponse
     * @param servletResponse
     * @throws IOException
     */
    private  void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            copyResponseHeader(servletRequest, servletResponse, header);
        }
    }

    /**
     * 拷贝response的header
     *
     * @param servletRequest
     * @param servletResponse
     * @param header
     */
    private  void copyResponseHeader(HttpServletRequest servletRequest,
                                           HttpServletResponse servletResponse, Header header) {
        String headerName = header.getName();
        if (hopByHopHeaders.containsHeader(headerName))
            return;
        String headerValue = header.getValue();
        if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE) ||
                headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
            copyProxyCookie(servletRequest, servletResponse, headerValue);
        } else if (headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
            // LOCATION Header may have to be rewritten.
            servletResponse.addHeader(headerName, rewriteUrlFromResponse(servletRequest, headerValue));
        } else {
            servletResponse.addHeader(headerName, headerValue);
        }
    }

    private static String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
        //TODO document example paths
        final String targetUri = servletRequest.getRequestURI();
        if (theUrl.startsWith(targetUri)) {
            /*-
             * The URL points back to the back-end server.
             * Instead of returning it verbatim we replace the target path with our
             * source path in a way that should instruct the original client to
             * request the URL pointed through this forward.
             * We do this by taking the current request and rewriting the path part
             * using this servlet's absolute path and the path from the returned URL
             * after the base target URL.
             */
            StringBuffer curUrl = servletRequest.getRequestURL();//no query
            int pos;
            // Skip the protocol part
            if ((pos = curUrl.indexOf("://")) >= 0) {
                // Skip the authority part
                // + 3 to skip the separator between protocol and authority
                if ((pos = curUrl.indexOf("/", pos + 3)) >= 0) {
                    // Trim everything after the authority part.
                    curUrl.setLength(pos);
                }
            }
            // Context path starts with a / if it is not blank
            curUrl.append(servletRequest.getContextPath());
            // Servlet path starts with a / if it is not blank
            curUrl.append(servletRequest.getServletPath());
            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

    /**
     * 拷贝cookie
     *
     * @param servletRequest
     * @param servletResponse
     * @param headerValue
     */
    private  void copyProxyCookie(HttpServletRequest servletRequest, HttpServletResponse servletResponse, String headerValue) {
        //build path for resulting cookie
        String path = servletRequest.getContextPath(); // path starts with / or is empty string
        path += servletRequest.getServletPath(); // servlet path starts with / or is empty string
        if (path.isEmpty()) {
            path = "/";
        }
        for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
            //set cookie name prefixed w/ a forward value so it won't collide w/ other cookies
            String proxyCookieName = doPreserveCookies ? cookie.getName() : getCookieNamePrefix(cookie.getName()) + cookie.getName();
            Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
            servletCookie.setComment(cookie.getComment());
            servletCookie.setMaxAge((int) cookie.getMaxAge());
            servletCookie.setPath(path); //set to the path of the forward servlet
            // don't set cookie domain
            servletCookie.setSecure(cookie.getSecure());
            servletCookie.setVersion(cookie.getVersion());
            servletCookie.setHttpOnly(cookie.isHttpOnly());
            servletResponse.addCookie(servletCookie);
        }
    }
    /**
     * 为转发请求设置header: X-Forwarded-Proto
     *
     * @param servletRequest
     * @param forwardRequest
     */
    private  void setXForwardedForHeader(HttpServletRequest servletRequest, HttpRequest forwardRequest) {
        if (doForwardIP) {

            String forHeader = servletRequest.getRemoteAddr();
            String existingForHeader = servletRequest.getHeader(forHeaderName);
            if (existingForHeader != null) {
                forHeader = existingForHeader + ", " + forHeader;
            }
            forwardRequest.setHeader(forHeaderName, forHeader);


            String protoHeader = servletRequest.getScheme();
            forwardRequest.setHeader(protoHeaderName, protoHeader);
        }
    }


    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[]{
                "Connection", "Keep-Alive", "forward-Authenticate", "forward-Authorization",
                "TE", "Trailers", "Transfer-Encoding", "Upgrade"};
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }
    /**
     * 拷贝请求Request中的header
     *
     * @param servletRequest
     * @param proxyRequest
     */
    private void copyHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest)  {
        Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = enumerationOfHeaderNames.nextElement();
            copyRequestHeader(servletRequest, proxyRequest, headerName);
        }
    }
    /**
     * 拷贝Request中的header
     *
     * @param servletRequest
     * @param proxyRequest
     * @param headerName
     */
    private void copyRequestHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest, String headerName)  {
        //Instead the content-length is effectively set via InputStreamEntity
        if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
            return;
        if (hopByHopHeaders.containsHeader(headerName))
            return;
        Enumeration<String> headers = servletRequest.getHeaders(headerName);
        while (headers.hasMoreElements()) {//sometimes more than one value
            String headerValue = headers.nextElement();
            // In case the forward host is running multiple virtual servers,
            // rewrite the Host header to ensure that we get content from
            // the correct virtual server
            if (!doPreserveHost && headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                URI uri = null;
                try {
                    uri = new URI(proxyRequest.getRequestLine().getUri());
                } catch (URISyntaxException e) {
                    log.error("uri创建失败",e);
                    throw new RuntimeException(e);
                }
                HttpHost host = URIUtils.extractHost(uri);
                headerValue = host.getHostName();
                if (host.getPort() != -1)
                    headerValue += ":" + host.getPort();
            } else if (!doPreserveCookies && headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
                headerValue = getRealCookie(headerValue);
            }
            proxyRequest.addHeader(headerName, headerValue);
        }
    }

    private static String getCookieNamePrefix(String name) {
        return "!forward!";
    }

    private static String getRealCookie(String cookieValue) {
        StringBuilder escapedCookie = new StringBuilder();
        String cookies[] = cookieValue.split("[;,]");
        for (String cookie : cookies) {
            String cookieSplit[] = cookie.split("=");
            if (cookieSplit.length == 2) {
                String cookieName = cookieSplit[0].trim();
                if (cookieName.startsWith(getCookieNamePrefix(cookieName))) {
                    cookieName = cookieName.substring(getCookieNamePrefix(cookieName).length());
                    if (escapedCookie.length() > 0) {
                        escapedCookie.append("; ");
                    }
                    escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
                }
            }
        }
        return escapedCookie.toString();
    }
    /**
     * 初始化一个转发请求
     *
     * @param reusableHttpServletRequest
     * @param forwardToUri
     * @return
     */
    private static HttpRequest initForwardRequest(HttpServletRequest reusableHttpServletRequest, String forwardToUri) throws IOException {

        // 这两个header任意一个存在就表示存在body
        if (reusableHttpServletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
                reusableHttpServletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            HttpEntityEnclosingRequest forwardRequest = new BasicHttpEntityEnclosingRequest(reusableHttpServletRequest.getMethod(), forwardToUri);
            //  note: we don't bother ensuring we close the servletInputStream since the container handles it
            String contentLength = reusableHttpServletRequest.getHeader("Content-Length");
            InputStreamEntity entity = new InputStreamEntity(reusableHttpServletRequest.getInputStream(), Optional.ofNullable(contentLength).map(Long::parseLong).orElse(-1L));
            forwardRequest.setEntity(entity);

            return forwardRequest;
        }
        return new BasicHttpRequest(reusableHttpServletRequest.getMethod(), forwardToUri);
    }


    private  String rewriteUrlFromRequest(HttpServletRequest servletRequest,URI targetUri) {
        StringBuilder uri = new StringBuilder(500);
        uri.append(targetUri);
        // Handle the path given to the servlet
        String pathInfo = servletRequest.getServletPath();
        if (pathInfo != null) {//ex: /my/path.html
            // getPathInfo() returns decoded string, so we need encodeUriQuery to encode "%" characters
            uri.append(encodeUriQuery(pathInfo, true));
        }
        // Handle the query string & fragment
        String queryString = servletRequest.getQueryString();//ex:(following '?'): name=value&foo=bar#fragment
        String fragment = null;
        //split off fragment from queryString, updating queryString if found
        if (queryString != null) {
            int fragIdx = queryString.indexOf('#');
            if (fragIdx >= 0) {
                fragment = queryString.substring(fragIdx + 1);
                queryString = queryString.substring(0, fragIdx);
            }
        }

        if (queryString != null && queryString.length() > 0) {
            uri.append('?');
            // queryString is not decoded, so we need encodeUriQuery not to encode "%" characters, to avoid double-encoding
            uri.append(encodeUriQuery(queryString, false));
        }

        if (doSendUrlFragment && fragment != null) {
            uri.append('#');
            // fragment is not decoded, so we need encodeUriQuery not to encode "%" characters, to avoid double-encoding
            uri.append(encodeUriQuery(fragment, false));
        }
        return uri.toString();
    }





    private static CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
        //Note that I can't simply use URI.java to encode because it will escape pre-existing escaped things.
        StringBuilder outBuf = null;
        Formatter formatter = null;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            boolean escape = true;
            if (c < 128) {
                if (asciiQueryChars.get((int) c) && !(encodePercent && c == '%')) {
                    escape = false;
                }
            } else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) {//not-ascii
                escape = false;
            }
            if (!escape) {
                if (outBuf != null)
                    outBuf.append(c);
            } else {
                //escape
                if (outBuf == null) {
                    outBuf = new StringBuilder(in.length() + 5 * 3);
                    outBuf.append(in, 0, i);
                    formatter = new Formatter(outBuf);
                }
                //leading %, 0 padded, width 2, capital hex
                formatter.format("%%%02X", (int) c);//TODO
            }
        }
        return outBuf != null ? outBuf : in;
    }



}
