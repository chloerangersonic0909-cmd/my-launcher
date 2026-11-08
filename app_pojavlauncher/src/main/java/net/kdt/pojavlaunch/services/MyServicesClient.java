package net.kdt.pojavlaunch.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import net.kdt.pojavlaunch.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android client for the first-party MY SERVICES API. */
public final class MyServicesClient {
    private static final String TAG = "MyServices";
    private static final String PREFS = "my_services_session";
    private static final String ACCESS_TOKEN = "access_token";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "my-services-io");
        thread.setDaemon(true);
        return thread;
    });
    public interface Callback<T> { void onSuccess(T value); void onError(String code, String message); }
    public static final class Service {
        public final String key, name, description, apiVersion;
        public Service(String key, String name, String description, String apiVersion) {
            this.key=key; this.name=name; this.description=description; this.apiVersion=apiVersion;
        }
    }
    private interface Parser<T> { T parse(JSONObject body) throws Exception; }
    private MyServicesClient() {}
    public static boolean isConfigured() {
        return BuildConfig.MY_SERVICES_BASE_URL != null
                && !BuildConfig.MY_SERVICES_BASE_URL.trim().isEmpty()
                && BuildConfig.MY_SERVICES_BASE_URL.startsWith("https://");
    }
    public static void fetchServices(Callback<Service[]> callback) {
        request("GET","/v1/services",null,null,callback,body -> {
            JSONArray array=body.getJSONArray("services"); Service[] result=new Service[array.length()];
            for(int i=0;i<array.length();i++){ JSONObject item=array.getJSONObject(i);
                result[i]=new Service(item.optString("service_key"),item.optString("display_name"),
                        item.optString("description"),item.optString("api_version","v1")); }
            return result;
        });
    }
    public static void checkHealth(Callback<JSONObject> callback) {
        request("GET","/health",null,null,callback,body -> body);
    }
    public static void saveAccessToken(Context context,String token) {
        if(token!=null&&!token.trim().isEmpty()) preferences(context).edit().putString(ACCESS_TOKEN,token).apply();
    }
    public static void clearSession(Context context) { preferences(context).edit().remove(ACCESS_TOKEN).apply(); }
    public static void fetchCurrentAccount(Context context,Callback<JSONObject> callback) {
        String token=preferences(context).getString(ACCESS_TOKEN,null);
        if(token==null){callback.onError("not_signed_in","No MY SERVICES session is stored.");return;}
        request("GET","/v1/account/me",null,token,callback,body -> body.optJSONObject("user"));
    }
    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static <T> void request(String method,String path,JSONObject payload,String token,
                                    Callback<T> callback,Parser<T> parser) {
        if(!isConfigured()){callback.onError("service_not_configured","MY_SERVICES_BASE_URL is not configured for this build.");return;}
        IO.execute(() -> {
            HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)new URL(BuildConfig.MY_SERVICES_BASE_URL+path).openConnection();
                connection.setRequestMethod(method); connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept","application/json");
                if(token!=null) connection.setRequestProperty("Authorization","Bearer "+token);
                if(payload!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                    try(OutputStream out=connection.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}}
                int status=connection.getResponseCode();
                InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
                StringBuilder text=new StringBuilder();
                if(stream!=null) try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                    String line;while((line=reader.readLine())!=null)text.append(line);}
                JSONObject body=text.length()==0?new JSONObject():new JSONObject(text.toString());
                if(status<200||status>=300) callback.onError(body.optString("error","http_"+status),body.optString("message","MY SERVICES request failed."));
                else callback.onSuccess(parser.parse(body));
            } catch(Exception error){Log.w(TAG,"MY SERVICES request failed",error);
                callback.onError("network_error",error.getMessage()==null?"Network request failed.":error.getMessage());}
            finally{if(connection!=null)connection.disconnect();}
        });
    }
}
