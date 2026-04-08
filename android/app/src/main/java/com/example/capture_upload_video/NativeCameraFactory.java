package com.example.capture_upload_video;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;
import java.util.Map;

public class NativeCameraFactory extends PlatformViewFactory {
    private final LifecycleOwner lifecycleOwner;

    // Recibe el ciclo de vida en el constructor
    public NativeCameraFactory(LifecycleOwner lifecycleOwner) {
        super(StandardMessageCodec.INSTANCE);
        this.lifecycleOwner = lifecycleOwner;
    }

    @NonNull
    @Override
    public PlatformView create(Context context, int id, Object args) {
        Map<String, Object> creationParams = (Map<String, Object>) args;
        // Se lo pasa a la vista final
        return new NativeCameraView(context, id, creationParams, lifecycleOwner);
    }
}