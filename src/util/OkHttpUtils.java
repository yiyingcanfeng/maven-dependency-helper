package util;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Map;

public class OkHttpUtils {

    private static Proxy proxy;

    public static void setProxy(String ip, int port, String username, String password) {
        if (OkHttpUtils.proxy == null) {
            OkHttpUtils.proxy = new Proxy(ip, port, username, password);
        }
    }

    public static OkHttpClient getmOkHttpClientUseProxy() {
        if (mOkHttpClientUseProxy == null) {
            mOkHttpClientUseProxy = new OkHttpClient();
        }
        return mOkHttpClientUseProxy;
    }


    static class Proxy {
        static String ip;
        static int port;
        static String username;
        static String password;
        static Authenticator proxyAuthenticator;
        static java.net.Proxy proxy;

        public Proxy(String ip, int port, String username, String password) {
            if (ip.equals(Proxy.ip) && Proxy.port == port && username.equals(Proxy.username) && password.equals(Proxy.password)) {
                return;
            }
            Proxy.ip = ip;
            Proxy.port = port;
            Proxy.username = username;
            Proxy.password = password;
            proxyAuthenticator = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(ip, port));
            OkHttpUtils.mOkHttpClientUseProxy = new OkHttpClient
                    .Builder()
                    .proxy(proxy)
                    .proxyAuthenticator(proxyAuthenticator)
                    .build();
        }
    }

    /**
     * 返回结果类型
     */
    public enum ResultType {
        /**
         * STRING:字符串类型
         * STREAM:流
         */
        STRING, STREAM
    }

    /**
     * 请求方式
     */
    public enum RequestMethod {
        /**
         * SYNC:同步请求
         * ASYNC:异步请求
         */
        GET, POST, DELETE, UPDATE, PUT
    }

    /**
     * 请求方式
     */
    public enum RequestType {
        /**
         * SYNC:同步请求
         * ASYNC:异步请求
         */
        SYNC, ASYNC
    }

    private static final OkHttpClient mOkHttpClient = new OkHttpClient();

    private static OkHttpClient mOkHttpClientUseProxy;

    public static final MediaType JSON = MediaType.parse("application/json;charset=utf-8");

    private static final Logger logger = LoggerFactory.getLogger(OkHttpUtils.class);

    public static OkHttpClient getOkHttpClient() {
        return mOkHttpClient;
    }

    /**
     * 发起get请求,获取字符串数据
     *
     * @param url url
     * @return 字符串
     */
    public static String get(String url) {
        mOkHttpClient.proxy();
        return stringGet(url, null);
    }

    /**
     * 发起get请求,获取字符串数据
     *
     * @param url url
     * @return 字符串
     */
    public static String get(String url, boolean useProxy) {
        return stringGet(url, null, useProxy);
    }

    /**
     * 发起get请求,获取字符串数据
     *
     * @param url   url
     * @param param param
     * @return 字符串
     */
    public static String get(String url, Map<String, String> param) {
        String result = "";
        url += "?";
        StringBuilder urlBuilder = new StringBuilder(url);
        for (Map.Entry<String, String> entry : param.entrySet()) {
            urlBuilder.append(String.format("%s=%s&", entry.getKey(), entry.getValue()));
        }
        int lastIndexOf = urlBuilder.lastIndexOf("&");
        if (lastIndexOf == urlBuilder.length() - 1) {
            urlBuilder.deleteCharAt(lastIndexOf);
        }
        url = urlBuilder.toString();
        Request request = getRequest(url, null, RequestMethod.GET);
        return getString(result, request);
    }

    /**
     * 发起get请求
     *
     * @param url    url
     * @param type   请求字符串数据还是二进制数据
     * @param header 请求头
     * @return 字符串
     */
    public static Object get(String url, ResultType type, Map<String, String> header) {
        switch (type) {
            //请求字符串数据
            case STRING:
                return stringGet(url, header);
            //请求二进制数据
            case STREAM:
                return streamGet(url, header);
            default:
                return "";
        }
    }

    /**
     * 发起get请求
     *
     * @param url    url
     * @param type   请求字符串数据还是二进制数据
     * @param header 请求头
     * @return 字符串
     */
    public static Object get(String url, ResultType type, String json, Map<String, String> header) {
        switch (type) {
            //请求字符串数据
            case STRING:
                return stringGet(url, header);
            //请求二进制数据
            case STREAM:
                return streamGet(url, header);
            default:
                return "";
        }
    }

    /**
     * 发起post请求, 发送表单数据
     *
     * @param url            url
     * @param requestFormMap 键值对的form表单数据
     * @return
     * @throws IOException
     */
    public static String post(String url, Map<String, String> requestFormMap) throws IOException {
        return post(url, RequestType.SYNC, requestFormMap, null, false);
    }

    /**
     * 发起post请求, 发送表单数据，携带自定义header
     *
     * @param url            url
     * @param requestFormMap 键值对的form表单数据
     * @param header         键值对的自定义header
     * @return
     * @throws IOException
     */
    public static String post(String url, Map<String, String> requestFormMap, Map<String, String> header) throws IOException {
        return post(url, RequestType.SYNC, requestFormMap, header, false);
    }

    /**
     * 发起post请求, 发送表单数据
     *
     * @param url  url
     * @param json json数据
     * @return
     * @throws IOException
     */
    public static String post(String url, String json) throws IOException {
        return post(url, RequestType.SYNC, json, null, false);
    }

    /**
     * 发起post请求, 发送表单数据
     *
     * @param url  url
     * @param json json数据
     * @return
     * @throws IOException
     */
    public static String post(String url, String json, boolean useProxy) throws IOException {
        return post(url, RequestType.SYNC, json, null, useProxy);
    }

    /**
     * 发起post请求
     *
     * @param requestType    同步请求还是异步请求
     * @param url            url
     * @param requestFormMap 键值对形式的表单数据
     * @param header         请求头
     * @return 字符串
     */
    public static String post(String url, RequestType requestType, Map<String, String> requestFormMap, Map<String, String> header, boolean useProxy) throws IOException {
        return stringPost(url, requestType, requestFormMap, header, useProxy);
    }

    /**
     * 发送post请求，获取字符数据
     *
     * @param url         url
     * @param requestType 同步请求还是异步请求
     * @param json        json数据
     * @param header      请求头
     * @return json字符串
     * @throws IOException IOException
     */
    public static String post(String url, RequestType requestType, String json, Map<String, String> header, boolean useProxy) throws IOException {
        return getString(url, requestType, json, header, RequestMethod.POST, useProxy);
    }

    /**
     * 发起put请求，获取字符数据
     *
     * @param url         url
     * @param requestType 同步请求还是异步请求
     * @param json        json数据
     * @param header      请求头
     * @return json字符串
     * @throws IOException IOException
     */
    public static String put(String url, RequestType requestType, String json, Map<String, String> header) throws IOException {
        return getString(url, requestType, json, header, RequestMethod.PUT, false);
    }

    /**
     * 发起delete请求，获取字符数据
     *
     * @param url
     * @param header
     * @return
     */
    public static String delete(String url, Map<String, String> header) {
        Request request = getRequest(url, header, RequestMethod.DELETE);
        return getString("", request);
    }

    /**
     * 发起get请求，获取字符数据
     *
     * @param url    url
     * @param header 请求头
     * @return 字符串
     */
    private static String stringGet(String url, Map<String, String> header) {
        String result = "";
        Request request = getRequest(url, header, RequestMethod.GET);
        return getString(result, request);
    }

    /**
     * 发起get请求，获取字符数据
     *
     * @param url    url
     * @param header 请求头
     * @return 字符串
     */
    private static String stringGet(String url, Map<String, String> header, boolean useProxy) {
        String result = "";
        Request request = getRequest(url, header, RequestMethod.GET);
        return getString(result, request, useProxy);
    }

    /**
     * 发起get请求，获取字符数据，携带json数据
     *
     * @param url    url
     * @param json   需要携带的json数据
     * @param header 请求头
     * @return 字符串
     */
    private static String stringGet(String url, String json, Map<String, String> header) {
        String result = "";
        RequestBody body = RequestBody.create(JSON, json);
        Request request = getRequest(url, header, body, RequestMethod.GET);
        return getString(result, request);
    }

    /**
     * get请求获取string数据
     *
     * @param result
     * @param request
     * @return
     */
    private static String getString(String result, Request request) {
        try {
            Response response = mOkHttpClient.newCall(request).execute();
            if (response.body() != null) {
                result = response.body().string();
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return result;
    }

    /**
     * get请求获取string数据
     *
     * @param result
     * @param request
     * @return
     */
    private static String getString(String result, Request request, boolean useProxy) {
        try {
            Response response;
            if (useProxy) {
                response = mOkHttpClientUseProxy.newCall(request).execute();
            } else {
                response = mOkHttpClient.newCall(request).execute();
            }
            if (response.body() != null) {
                result = response.body().string();
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return result;
    }


    /**
     * 发起get请求,获取二进制数据
     *
     * @param url    url
     * @param header header
     * @return InputStream
     */
    private static InputStream streamGet(String url, Map<String, String> header) {
        InputStream is = null;
        Request request = getRequest(url, header, RequestMethod.GET);
        try {
            Response response = mOkHttpClient.newCall(request).execute();
            if (response.body() != null) {
                is = response.body().byteStream();
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return is;
    }

    /**
     * @param url    url
     * @param header header
     * @return Request
     */
    private static Request getRequest(String url, Map<String, String> header, RequestMethod requestMethod) {
        Request request;
        if (header == null) {
            if (requestMethod == RequestMethod.DELETE) {
                request = new Request.Builder().url(url).delete().build();
            } else {
                request = new Request.Builder().url(url).build();
            }
        } else {
            Headers headerBuild = Headers.of(header);
            if (requestMethod == RequestMethod.DELETE) {
                request = new Request.Builder().url(url).delete().headers(headerBuild).build();
            } else {
                request = new Request.Builder().url(url).headers(headerBuild).build();

            }
        }
        return request;
    }

    /**
     * @param url         url
     * @param header      header
     * @param requestBody requestBody
     * @return Request
     */
    private static Request getRequest(String url, Map<String, String> header, RequestBody requestBody, RequestMethod requestMethod) {
        Request request;
        if (header == null) {
            if (requestMethod == RequestMethod.PUT) {
                request = new Request.Builder().url(url).put(requestBody).build();
            } else if (requestMethod == RequestMethod.GET) {
                request = new Request.Builder().url(url).post(requestBody).build();
            } else if (requestMethod == RequestMethod.POST) {
                request = new Request.Builder().url(url).post(requestBody).build();
            } else {
                request = new Request.Builder().url(url).post(requestBody).build();
            }
        } else {
            Headers headerBuild = Headers.of(header);
            if (requestMethod == RequestMethod.PUT) {
                request = new Request.Builder().url(url).put(requestBody).headers(headerBuild).build();
            } else if (requestMethod == RequestMethod.GET) {
                request = new Request.Builder().url(url).post(requestBody).headers(headerBuild).build();
            } else if (requestMethod == RequestMethod.POST) {
                request = new Request.Builder().url(url).post(requestBody).headers(headerBuild).build();
            } else {
                request = new Request.Builder().url(url).post(requestBody).headers(headerBuild).build();
            }
        }
        return request;
    }

    /**
     * 发送httppost请求，获取字符数据
     *
     * @param requestType    同步请求还是异步请求
     * @param url            url
     * @param requestFormMap 键值对形式的数据
     * @param header         请求头
     * @return 字符串数据
     */
    private static String stringPost(String url, RequestType requestType, Map<String, String> requestFormMap, Map<String, String> header, boolean useProxy) throws IOException {
        final String[] result = {""};
        FormBody.Builder builder = new FormBody.Builder();
        for (String key : requestFormMap.keySet()) {
            builder.add(key, requestFormMap.get(key));
        }
        RequestBody requestBody = builder.build();
        Request request = getRequest(url, header, requestBody, RequestMethod.POST);

        return getString(requestType, result, request, useProxy);
    }

    /**
     * 发送post请求，获取字符数据
     *
     * @param url         url
     * @param requestType 同步请求还是异步请求
     * @param json        json数据
     * @param header      请求头
     * @return json字符串
     * @throws IOException IOException
     */
    private static String getString(String url, RequestType requestType, String json, Map<String, String> header, RequestMethod requestMethod, boolean useProxy) throws IOException {
        final String[] result = {""};
        RequestBody body = RequestBody.create(JSON, json);
        Request request = getRequest(url, header, body, requestMethod);

        return getString(requestType, result, request, useProxy);
    }

    /**
     * @param requestType 同步请求还是异步请求
     * @param result      result
     * @param request     request
     * @return 字符串
     * @throws IOException IOException
     */
    private static String getString(RequestType requestType, final String[] result, Request request, boolean useProxy) throws IOException {
        switch (requestType) {
            //同步请求
            case SYNC:
                Response response;
                if (useProxy) {
                    response = OkHttpUtils.getmOkHttpClientUseProxy().newCall(request).execute();
                } else {
                    response = OkHttpUtils.mOkHttpClient.newCall(request).execute();
                }
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        result[0] = response.body().string();
                    }
                } else {
                    int errorCode = response.code();
                    String message = "";
                    if (response.body() != null) {
                        message = response.body().string();
                    }
                }
                break;
            //异步请求
            case ASYNC:
                Call call;
                if (useProxy) {
                    call = OkHttpUtils.getmOkHttpClientUseProxy().newCall(request);
                } else {
                    call = OkHttpUtils.mOkHttpClient.newCall(request);
                }
                call.enqueue(new Callback() {

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.body() != null) {
                            result[0] = response.body().string();
                        } else {
                            int errorCode = response.code();
                            String message;
                            if (response.body() != null) {
                                message = response.body().string();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        logger.error(e.toString());
                    }
                });
                break;
            default:
                result[0] = "";
                break;
        }
        return result[0];
    }
}