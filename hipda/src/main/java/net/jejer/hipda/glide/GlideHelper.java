package net.jejer.hipda.glide;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.DiskLruCacheWrapper;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.Target;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import net.jejer.hipda.bean.HiSettingsHelper;
import net.jejer.hipda.cache.LRUCache;
import net.jejer.hipda.okhttp.LoggingInterceptor;
import net.jejer.hipda.okhttp.OkHttpHelper;
import net.jejer.hipda.ui.BaseFragment;
import net.jejer.hipda.ui.HiApplication;
import net.jejer.hipda.utils.Constants;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.Logger;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import jp.wasabeef.glide.transformations.CropCircleTransformation;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class GlideHelper {

    private static LRUCache<String, String> NOT_FOUND_AVATARS = new LRUCache<>(512);
    private static File AVATAR_CACHE_DIR;
    public static File DEFAULT_AVATAR_FILE;

    private static Drawable DEFAULT_USER_ICON;

    public final static long AVATAR_CACHE_MILLS = 7 * 24 * 60 * 60 * 1000;
    public final static long AVATAR_404_CACHE_MILLS = 24 * 60 * 60 * 1000;

    public static void init(Context context) {
        if (!Glide.isSetup()) {
            GlideBuilder gb = new GlideBuilder(context);

            gb.setDiskCache(DiskLruCacheWrapper.get(Glide.getPhotoCacheDir(context), 150 * 1024 * 1024));

            Glide.setup(gb);

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.connectTimeout(OkHttpHelper.NETWORK_TIMEOUT_SECS, TimeUnit.SECONDS)
                    .readTimeout(OkHttpHelper.NETWORK_TIMEOUT_SECS, TimeUnit.SECONDS)
                    .writeTimeout(OkHttpHelper.NETWORK_TIMEOUT_SECS, TimeUnit.SECONDS);

            if (Logger.isDebug())
                builder.addInterceptor(new LoggingInterceptor());

            final ProgressListener progressListener = new ProgressListener() {
                private long progressMark = 0;

                @Override
                public void update(String url, long bytesRead, long contentLength, boolean done) {
                    if (SystemClock.uptimeMillis() - progressMark > 50) {
                        int progress = (int) Math.round((100.0 * bytesRead) / contentLength);
                        EventBus.getDefault().post(new GlideImageEvent(url, progress, Constants.STATUS_IN_PROGRESS));
                        progressMark = SystemClock.uptimeMillis();
                    }
                }
            };

            builder.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Response originalResponse = chain.proceed(chain.request());
                    String url = chain.request().url().toString();
                    //avatar don't need a progress listener
                    if (url.startsWith(HiUtils.AvatarBaseUrl)) {
                        return originalResponse;
                    }
                    return originalResponse.newBuilder()
                            .body(new ProgressResponseBody(url, originalResponse.body(), progressListener))
                            .build();
                }
            });

            OkHttpClient client = builder.build();

            Glide.get(context).register(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(client));

            AVATAR_CACHE_DIR = Glide.getPhotoCacheDir(context, "avatar");

            DEFAULT_USER_ICON = new IconicsDrawable(HiApplication.getAppContext(), GoogleMaterial.Icon.gmd_account_box).color(Color.LTGRAY).sizeDp(64);
            DEFAULT_AVATAR_FILE = new File(AVATAR_CACHE_DIR, "default.png");
            if (!DEFAULT_AVATAR_FILE.exists()) {
                try {
                    Bitmap b = drawableToBitmap(DEFAULT_USER_ICON);
                    b.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(DEFAULT_AVATAR_FILE));
                    b.recycle();
                } catch (Exception e) {
                    Logger.e(e);
                }
            }
        }
    }

    public static boolean ready() {
        return Glide.isSetup();
    }

    public static void loadAvatar(BaseFragment fragment, ImageView view, String avatarUrl) {
        if (isOkToLoad(fragment)) {
            loadAvatar(Glide.with(fragment), view, avatarUrl);
        }
    }

    public static void loadAvatar(RequestManager glide, ImageView view, String avatarUrl) {
        if (NOT_FOUND_AVATARS.containsKey(avatarUrl)) {
            avatarUrl = DEFAULT_AVATAR_FILE.getAbsolutePath();
        }
        if (HiSettingsHelper.getInstance().getBooleanValue(HiSettingsHelper.PERF_CIRCLE_AVATAR, false)) {
            glide.load(avatarUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(DEFAULT_USER_ICON)
                    .crossFade()
                    .bitmapTransform(new CropCircleTransformation(HiApplication.getAppContext()))
                    .into(view);
        } else {
            glide.load(avatarUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .centerCrop()
                    .error(DEFAULT_USER_ICON)
                    .crossFade()
                    .into(view);
        }
    }

    public static File getAvatarFile(Context ctx, String avatarUrl) {
        File f = null;
        try {
            FutureTarget<File> future = Glide.with(ctx)
                    .load(avatarUrl)
                    .downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
            f = future.get();
            Glide.clear(future);
        } catch (Exception ignored) {
        }
        return f;
    }

    public static void markAvatarNotFound(String avatarUrl) {
        NOT_FOUND_AVATARS.put(avatarUrl, "");
    }

    private static class ProgressResponseBody extends ResponseBody {

        private final ResponseBody responseBody;
        private final ProgressListener progressListener;
        private BufferedSource bufferedSource;
        private String url;

        public ProgressResponseBody(String url, ResponseBody responseBody, ProgressListener progressListener) {
            this.responseBody = responseBody;
            this.progressListener = progressListener;
            this.url = url;
        }

        @Override
        public MediaType contentType() {
            return responseBody.contentType();
        }

        @Override
        public long contentLength() {
            return responseBody.contentLength();
        }

        @Override
        public BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        private Source source(final Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override
                public long read(Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                    progressListener.update(url, totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }

    interface ProgressListener {
        void update(String url, long bytesRead, long contentLength, boolean done);

    }

    public static File getAvatarFile(String url) {
        return new File(GlideHelper.AVATAR_CACHE_DIR, url.substring(HiUtils.AvatarBaseUrl.length()).replace("/", "_"));
    }

    public static boolean isOkToLoad(Context activity) {
        if (activity != null
                && Build.VERSION.SDK_INT >= 17
                && activity instanceof Activity) {
            if (((Activity) activity).isDestroyed())
                return false;
        }
        return true;
    }

    public static boolean isOkToLoad(Fragment fragment) {
        return fragment != null && fragment.getActivity() != null && !fragment.isDetached();
    }


    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private static InputStream bitmapToInputStream(Bitmap bitmap) {
        int size = bitmap.getHeight() * bitmap.getRowBytes();
        ByteBuffer buffer = ByteBuffer.allocate(size);
        bitmap.copyPixelsToBuffer(buffer);
        return new ByteArrayInputStream(buffer.array());
    }

}
