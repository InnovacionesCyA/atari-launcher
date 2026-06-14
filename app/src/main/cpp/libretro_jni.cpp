/**
 * libretro_jni.cpp
 * JNI bridge between Android/Kotlin and the atari800 libretro core.
 *
 * The libretro API uses callbacks that the frontend (us) must implement.
 * We call retro_set_* to register our callbacks, then retro_init(),
 * retro_load_game(), and retro_run() in a tight loop on a dedicated thread.
 *
 * Video: XRGB8888 frames are blitted to an ANativeWindow via a pixel-copy path.
 * Audio: 16-bit stereo PCM via AudioTrack on the Java side (fed via JNI callback).
 */

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <string>
#include <cstring>
#include <cstdint>
#include <cstdlib>
#include <pthread.h>
#include <unistd.h>
#include <atomic>
#include <vector>

#define TAG "AtariLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// ---------- libretro API types ----------
#define RETRO_API_VERSION 1

struct retro_game_info {
    const char *path;
    const void *data;
    size_t size;
    const char *meta;
};

struct retro_system_info {
    const char *library_name;
    const char *library_version;
    const char *valid_extensions;
    bool need_fullpath;
    bool block_extract;
};

struct retro_game_geometry {
    unsigned base_width;
    unsigned base_height;
    unsigned max_width;
    unsigned max_height;
    float aspect_ratio;
};

struct retro_system_timing {
    double fps;
    double sample_rate;
};

struct retro_system_av_info {
    retro_game_geometry geometry;
    retro_system_timing timing;
};

struct retro_variable {
    const char *key;
    const char *value;
};

// Environment command constants
#define RETRO_ENVIRONMENT_GET_CAN_DUPE          3
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT      10
#define RETRO_ENVIRONMENT_GET_VARIABLE          15
#define RETRO_ENVIRONMENT_SET_VARIABLES         16
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE   17
#define RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE  23
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE     27
#define RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY 30
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY    31
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY  9
#define RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO    32
#define RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO    34
#define RETRO_ENVIRONMENT_SET_CONTROLLER_INFO   35
#define RETRO_ENVIRONMENT_SET_MEMORY_MAPS       36
#define RETRO_ENVIRONMENT_SET_GEOMETRY          37
#define RETRO_ENVIRONMENT_GET_USERNAME          38
#define RETRO_ENVIRONMENT_GET_LANGUAGE          39
#define RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME   47

enum retro_pixel_format {
    RETRO_PIXEL_FORMAT_0RGB1555 = 0,
    RETRO_PIXEL_FORMAT_XRGB8888 = 1,
    RETRO_PIXEL_FORMAT_RGB565   = 2,
    RETRO_PIXEL_FORMAT_UNKNOWN  = INT_MAX
};

// Input device/index constants
#define RETRO_DEVICE_JOYPAD     1
#define RETRO_DEVICE_ID_JOYPAD_B        0
#define RETRO_DEVICE_ID_JOYPAD_Y        1
#define RETRO_DEVICE_ID_JOYPAD_SELECT   2
#define RETRO_DEVICE_ID_JOYPAD_START    3
#define RETRO_DEVICE_ID_JOYPAD_UP       4
#define RETRO_DEVICE_ID_JOYPAD_DOWN     5
#define RETRO_DEVICE_ID_JOYPAD_LEFT     6
#define RETRO_DEVICE_ID_JOYPAD_RIGHT    7
#define RETRO_DEVICE_ID_JOYPAD_A        8
#define RETRO_DEVICE_ID_JOYPAD_X        9
#define RETRO_DEVICE_ID_JOYPAD_L        10
#define RETRO_DEVICE_ID_JOYPAD_R        11

struct retro_log_callback {
    void (*log)(int level, const char *fmt, ...);
};

// ---------- Function pointer types ----------
typedef void (*retro_set_environment_t)(bool (*)(unsigned, void *));
typedef void (*retro_set_video_refresh_t)(void (*)(const void *, unsigned, unsigned, size_t));
typedef void (*retro_set_audio_sample_t)(void (*)(int16_t, int16_t));
typedef void (*retro_set_audio_sample_batch_t)(size_t (*)(const int16_t *, size_t));
typedef void (*retro_set_input_poll_t)(void (*)());
typedef void (*retro_set_input_state_t)(int16_t (*)(unsigned, unsigned, unsigned, unsigned));
typedef void (*retro_init_t)();
typedef void (*retro_deinit_t)();
typedef unsigned (*retro_api_version_t)();
typedef void (*retro_get_system_info_t)(struct retro_system_info *);
typedef void (*retro_get_system_av_info_t)(struct retro_system_av_info *);
typedef bool (*retro_load_game_t)(const struct retro_game_info *);
typedef void (*retro_unload_game_t)();
typedef void (*retro_run_t)();
typedef void (*retro_reset_t)();
typedef size_t (*retro_serialize_size_t)();
typedef bool (*retro_serialize_t)(void *, size_t);
typedef bool (*retro_unserialize_t)(const void *, size_t);
typedef void (*retro_set_controller_port_device_t)(unsigned, unsigned);

// ---------- Global state ----------
static void *g_core_handle = nullptr;

// Function pointers
static retro_set_environment_t          fp_retro_set_environment = nullptr;
static retro_set_video_refresh_t        fp_retro_set_video_refresh = nullptr;
static retro_set_audio_sample_t         fp_retro_set_audio_sample = nullptr;
static retro_set_audio_sample_batch_t   fp_retro_set_audio_sample_batch = nullptr;
static retro_set_input_poll_t           fp_retro_set_input_poll = nullptr;
static retro_set_input_state_t          fp_retro_set_input_state = nullptr;
static retro_init_t                     fp_retro_init = nullptr;
static retro_deinit_t                   fp_retro_deinit = nullptr;
static retro_api_version_t              fp_retro_api_version = nullptr;
static retro_get_system_info_t          fp_retro_get_system_info = nullptr;
static retro_get_system_av_info_t       fp_retro_get_system_av_info = nullptr;
static retro_load_game_t                fp_retro_load_game = nullptr;
static retro_unload_game_t              fp_retro_unload_game = nullptr;
static retro_run_t                      fp_retro_run = nullptr;
static retro_reset_t                    fp_retro_reset = nullptr;
static retro_serialize_size_t           fp_retro_serialize_size = nullptr;
static retro_serialize_t                fp_retro_serialize = nullptr;
static retro_unserialize_t              fp_retro_unserialize = nullptr;
static retro_set_controller_port_device_t fp_retro_set_controller_port_device = nullptr;

// Runtime state
static ANativeWindow *g_window = nullptr;
static pthread_mutex_t g_window_mutex = PTHREAD_MUTEX_INITIALIZER;

static std::atomic<bool> g_running{false};
static std::atomic<bool> g_paused{false};
static pthread_t g_emu_thread;

// Pixel format the core requested
static retro_pixel_format g_pixel_format = RETRO_PIXEL_FORMAT_RGB565;

// AV info
static retro_system_av_info g_av_info = {};

// Input state (bitmask per button)
static std::atomic<uint32_t> g_input_state{0};

// Frame buffer for copying to surface
static std::vector<uint32_t> g_framebuffer;
static unsigned g_fb_width = 0;
static unsigned g_fb_height = 0;
static pthread_mutex_t g_fb_mutex = PTHREAD_MUTEX_INITIALIZER;

// Audio callback storage
static JavaVM *g_jvm = nullptr;
static jobject g_audio_track_obj = nullptr;  // global ref
static jmethodID g_audio_write_method = nullptr;

// Save state paths
static std::string g_save_dir;
static std::string g_system_dir;
static std::string g_content_dir;

// ---------- Logging ----------
static void core_log(int level, const char *fmt, ...) {
    char buf[2048];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    int prio = ANDROID_LOG_DEBUG;
    if (level == 1) prio = ANDROID_LOG_INFO;
    if (level == 2) prio = ANDROID_LOG_WARN;
    if (level == 3) prio = ANDROID_LOG_ERROR;
    __android_log_print(prio, "Atari800Core", "%s", buf);
}

// ---------- Libretro callbacks ----------
static bool environment_cb(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool*)data = true;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            g_pixel_format = *(retro_pixel_format*)data;
            LOGI("Core requested pixel format: %d", g_pixel_format);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *(const char **)data = g_system_dir.c_str();
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **)data = g_save_dir.c_str();
            return true;

        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:
            *(const char **)data = g_content_dir.c_str();
            return true;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            retro_log_callback *log = (retro_log_callback*)data;
            log->log = core_log;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            retro_variable *var = (retro_variable*)data;
            var->value = nullptr;
            return false; // use defaults
        }

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            return true;

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *(bool*)data = false;
            return true;

        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            return true;

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            retro_game_geometry *geo = (retro_game_geometry*)data;
            g_av_info.geometry = *geo;
            return true;
        }

        default:
            return false;
    }
}

static void video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return;

    pthread_mutex_lock(&g_fb_mutex);
    g_fb_width = width;
    g_fb_height = height;

    // Convert to ARGB8888 for ANativeWindow
    g_framebuffer.resize(width * height);

    if (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
        // Direct copy - already 32bpp
        const uint32_t *src = (const uint32_t*)data;
        for (unsigned y = 0; y < height; y++) {
            const uint32_t *row = (const uint32_t*)((const uint8_t*)data + y * pitch);
            for (unsigned x = 0; x < width; x++) {
                g_framebuffer[y * width + x] = row[x] | 0xFF000000;
            }
        }
    } else if (g_pixel_format == RETRO_PIXEL_FORMAT_RGB565) {
        // Convert RGB565 → ARGB8888
        for (unsigned y = 0; y < height; y++) {
            const uint16_t *row = (const uint16_t*)((const uint8_t*)data + y * pitch);
            for (unsigned x = 0; x < width; x++) {
                uint16_t p = row[x];
                uint8_t r = ((p >> 11) & 0x1F) << 3;
                uint8_t g = ((p >> 5)  & 0x3F) << 2;
                uint8_t b = (p & 0x1F) << 3;
                g_framebuffer[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }
    } else {
        // 0RGB1555 → ARGB8888
        for (unsigned y = 0; y < height; y++) {
            const uint16_t *row = (const uint16_t*)((const uint8_t*)data + y * pitch);
            for (unsigned x = 0; x < width; x++) {
                uint16_t p = row[x];
                uint8_t r = ((p >> 10) & 0x1F) << 3;
                uint8_t g = ((p >> 5)  & 0x1F) << 3;
                uint8_t b = (p & 0x1F) << 3;
                g_framebuffer[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }
    pthread_mutex_unlock(&g_fb_mutex);

    // Blit to ANativeWindow
    pthread_mutex_lock(&g_window_mutex);
    if (g_window) {
        ANativeWindow_setBuffersGeometry(g_window, (int32_t)width, (int32_t)height, WINDOW_FORMAT_RGBX_8888);
        ANativeWindow_Buffer buf;
        if (ANativeWindow_lock(g_window, &buf, nullptr) == 0) {
            pthread_mutex_lock(&g_fb_mutex);
            for (unsigned y = 0; y < height && y < (unsigned)buf.height; y++) {
                uint32_t *dst = (uint32_t*)buf.bits + y * buf.stride;
                const uint32_t *src = g_framebuffer.data() + y * width;
                memcpy(dst, src, width * sizeof(uint32_t));
            }
            pthread_mutex_unlock(&g_fb_mutex);
            ANativeWindow_unlockAndPost(g_window);
        }
    }
    pthread_mutex_unlock(&g_window_mutex);
}

// Audio batch callback - called by core with PCM samples
static size_t audio_sample_batch_cb(const int16_t *data, size_t frames) {
    if (!g_jvm || !g_audio_track_obj || !g_audio_write_method) return frames;

    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        attached = true;
    }

    if (env) {
        jsize count = (jsize)(frames * 2); // stereo
        jshortArray arr = env->NewShortArray(count);
        if (arr) {
            env->SetShortArrayRegion(arr, 0, count, data);
            env->CallVoidMethod(g_audio_track_obj, g_audio_write_method, arr, 0, count);
            env->DeleteLocalRef(arr);
        }
    }

    if (attached) g_jvm->DetachCurrentThread();
    return frames;
}

static void audio_sample_cb(int16_t left, int16_t right) {
    int16_t buf[2] = {left, right};
    audio_sample_batch_cb(buf, 1);
}

static void input_poll_cb() {
    // Input state is updated from JNI calls from the touch overlay
}

static int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;
    if (device != RETRO_DEVICE_JOYPAD) return 0;
    uint32_t state = g_input_state.load();
    return (state >> id) & 1;
}

// ---------- Emulator thread ----------
struct EmuThreadArgs {
    std::string rom_path;
};

static void* emu_thread_func(void *arg) {
    EmuThreadArgs *args = (EmuThreadArgs*)arg;
    std::string rom_path = args->rom_path;
    delete args;

    LOGI("Emulator thread started for: %s", rom_path.c_str());

    retro_game_info game_info = {};
    game_info.path = rom_path.c_str();
    game_info.data = nullptr;
    game_info.size = 0;
    game_info.meta = "";

    retro_system_info sys_info = {};
    fp_retro_get_system_info(&sys_info);
    LOGI("Core: %s %s, needs_fullpath=%d", sys_info.library_name, sys_info.library_version, sys_info.need_fullpath);

    if (!sys_info.need_fullpath) {
        // Load file into memory
        FILE *f = fopen(rom_path.c_str(), "rb");
        if (!f) {
            LOGE("Failed to open ROM: %s", rom_path.c_str());
            g_running = false;
            return nullptr;
        }
        fseek(f, 0, SEEK_END);
        game_info.size = ftell(f);
        fseek(f, 0, SEEK_SET);
        void *buf = malloc(game_info.size);
        fread(buf, 1, game_info.size, f);
        fclose(f);
        game_info.data = buf;
    }

    bool loaded = fp_retro_load_game(&game_info);
    if (game_info.data) free((void*)game_info.data);

    if (!loaded) {
        LOGE("retro_load_game failed for: %s", rom_path.c_str());
        g_running = false;
        return nullptr;
    }

    fp_retro_get_system_av_info(&g_av_info);
    LOGI("AV info: %dx%d @ %.2f fps, sample_rate=%.0f",
         g_av_info.geometry.base_width, g_av_info.geometry.base_height,
         g_av_info.timing.fps, g_av_info.timing.sample_rate);

    double fps = g_av_info.timing.fps;
    if (fps <= 0.0) fps = 50.0; // Atari default
    long frame_ns = (long)(1000000000.0 / fps);

    while (g_running) {
        if (g_paused) {
            struct timespec ts = {0, 16000000}; // 16ms
            nanosleep(&ts, nullptr);
            continue;
        }

        struct timespec t1;
        clock_gettime(CLOCK_MONOTONIC, &t1);

        fp_retro_run();

        struct timespec t2;
        clock_gettime(CLOCK_MONOTONIC, &t2);
        long elapsed = (t2.tv_sec - t1.tv_sec) * 1000000000L + (t2.tv_nsec - t1.tv_nsec);
        long sleep_ns = frame_ns - elapsed;
        if (sleep_ns > 0) {
            struct timespec ts = {0, sleep_ns};
            nanosleep(&ts, nullptr);
        }
    }

    LOGI("Emulator thread stopping");
    fp_retro_unload_game();
    return nullptr;
}

// ---------- Helper: load symbol ----------
template<typename T>
static bool load_sym(void *handle, const char *name, T &out) {
    out = (T)dlsym(handle, name);
    if (!out) {
        LOGE("Symbol not found: %s", name);
        return false;
    }
    return true;
}

// ---------- JNI exports ----------
extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeInit(
        JNIEnv *env, jclass clazz,
        jstring jSaveDir, jstring jSystemDir, jstring jContentDir) {

    if (g_core_handle) {
        LOGI("Core already loaded");
        return JNI_TRUE;
    }

    const char *saveDir    = env->GetStringUTFChars(jSaveDir, nullptr);
    const char *systemDir  = env->GetStringUTFChars(jSystemDir, nullptr);
    const char *contentDir = env->GetStringUTFChars(jContentDir, nullptr);

    g_save_dir    = saveDir    ? saveDir    : "/data/data/com.atari.launcher/files/saves";
    g_system_dir  = systemDir  ? systemDir  : "/data/data/com.atari.launcher/files/system";
    g_content_dir = contentDir ? contentDir : "/data/data/com.atari.launcher/files/content";

    env->ReleaseStringUTFChars(jSaveDir, saveDir);
    env->ReleaseStringUTFChars(jSystemDir, systemDir);
    env->ReleaseStringUTFChars(jContentDir, contentDir);

    // Load the core .so
    g_core_handle = dlopen("libatari800_libretro_android.so", RTLD_LAZY | RTLD_LOCAL);
    if (!g_core_handle) {
        LOGE("dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }

    bool ok = true;
    ok &= load_sym(g_core_handle, "retro_set_environment",        fp_retro_set_environment);
    ok &= load_sym(g_core_handle, "retro_set_video_refresh",      fp_retro_set_video_refresh);
    ok &= load_sym(g_core_handle, "retro_set_audio_sample",       fp_retro_set_audio_sample);
    ok &= load_sym(g_core_handle, "retro_set_audio_sample_batch", fp_retro_set_audio_sample_batch);
    ok &= load_sym(g_core_handle, "retro_set_input_poll",         fp_retro_set_input_poll);
    ok &= load_sym(g_core_handle, "retro_set_input_state",        fp_retro_set_input_state);
    ok &= load_sym(g_core_handle, "retro_init",                   fp_retro_init);
    ok &= load_sym(g_core_handle, "retro_deinit",                 fp_retro_deinit);
    ok &= load_sym(g_core_handle, "retro_api_version",            fp_retro_api_version);
    ok &= load_sym(g_core_handle, "retro_get_system_info",        fp_retro_get_system_info);
    ok &= load_sym(g_core_handle, "retro_get_system_av_info",     fp_retro_get_system_av_info);
    ok &= load_sym(g_core_handle, "retro_load_game",              fp_retro_load_game);
    ok &= load_sym(g_core_handle, "retro_unload_game",            fp_retro_unload_game);
    ok &= load_sym(g_core_handle, "retro_run",                    fp_retro_run);
    ok &= load_sym(g_core_handle, "retro_reset",                  fp_retro_reset);
    ok &= load_sym(g_core_handle, "retro_serialize_size",         fp_retro_serialize_size);
    ok &= load_sym(g_core_handle, "retro_serialize",              fp_retro_serialize);
    ok &= load_sym(g_core_handle, "retro_unserialize",            fp_retro_unserialize);
    load_sym(g_core_handle, "retro_set_controller_port_device", fp_retro_set_controller_port_device); // optional

    if (!ok) {
        dlclose(g_core_handle);
        g_core_handle = nullptr;
        return JNI_FALSE;
    }

    // Register callbacks
    fp_retro_set_environment(environment_cb);
    fp_retro_set_video_refresh(video_refresh_cb);
    fp_retro_set_audio_sample(audio_sample_cb);
    fp_retro_set_audio_sample_batch(audio_sample_batch_cb);
    fp_retro_set_input_poll(input_poll_cb);
    fp_retro_set_input_state(input_state_cb);

    fp_retro_init();

    LOGI("Core initialized, API version: %u", fp_retro_api_version());
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeSetAudioTrack(
        JNIEnv *env, jclass clazz, jobject audioTrack) {
    if (g_audio_track_obj) {
        env->DeleteGlobalRef(g_audio_track_obj);
        g_audio_track_obj = nullptr;
    }
    if (audioTrack) {
        g_audio_track_obj = env->NewGlobalRef(audioTrack);
        jclass cls = env->GetObjectClass(g_audio_track_obj);
        g_audio_write_method = env->GetMethodID(cls, "write", "([SII)I");
        if (!g_audio_write_method) {
            LOGE("Could not find AudioTrack.write method");
        }
    }
}

JNIEXPORT jboolean JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeLoadGame(
        JNIEnv *env, jclass clazz, jstring jRomPath) {
    if (!g_core_handle || !fp_retro_load_game) return JNI_FALSE;

    const char *romPath = env->GetStringUTFChars(jRomPath, nullptr);
    std::string path(romPath ? romPath : "");
    env->ReleaseStringUTFChars(jRomPath, romPath);

    if (g_running) {
        g_running = false;
        pthread_join(g_emu_thread, nullptr);
    }

    g_running = true;
    g_paused  = false;
    g_input_state = 0;

    EmuThreadArgs *args = new EmuThreadArgs{path};
    pthread_create(&g_emu_thread, nullptr, emu_thread_func, args);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeStop(
        JNIEnv *env, jclass clazz) {
    if (g_running) {
        g_running = false;
        pthread_join(g_emu_thread, nullptr);
    }
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativePause(
        JNIEnv *env, jclass clazz) {
    g_paused = true;
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeResume(
        JNIEnv *env, jclass clazz) {
    g_paused = false;
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeSetSurface(
        JNIEnv *env, jclass clazz, jobject surface) {
    pthread_mutex_lock(&g_window_mutex);
    if (g_window) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
    if (surface) {
        g_window = ANativeWindow_fromSurface(env, surface);
        LOGI("Surface set: %p", g_window);
    }
    pthread_mutex_unlock(&g_window_mutex);
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeSetInput(
        JNIEnv *env, jclass clazz, jint buttonMask) {
    g_input_state = (uint32_t)buttonMask;
}

JNIEXPORT jboolean JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeSaveState(
        JNIEnv *env, jclass clazz, jstring jPath) {
    if (!g_core_handle || !fp_retro_serialize_size || !fp_retro_serialize) return JNI_FALSE;

    size_t sz = fp_retro_serialize_size();
    if (sz == 0) return JNI_FALSE;

    void *buf = malloc(sz);
    bool ok = fp_retro_serialize(buf, sz);
    if (ok) {
        const char *path = env->GetStringUTFChars(jPath, nullptr);
        FILE *f = fopen(path, "wb");
        if (f) {
            fwrite(buf, 1, sz, f);
            fclose(f);
        } else {
            ok = false;
        }
        env->ReleaseStringUTFChars(jPath, path);
    }
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeLoadState(
        JNIEnv *env, jclass clazz, jstring jPath) {
    if (!g_core_handle || !fp_retro_unserialize) return JNI_FALSE;

    const char *path = env->GetStringUTFChars(jPath, nullptr);
    FILE *f = fopen(path, "rb");
    if (!f) {
        env->ReleaseStringUTFChars(jPath, path);
        return JNI_FALSE;
    }
    fseek(f, 0, SEEK_END);
    size_t sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    void *buf = malloc(sz);
    fread(buf, 1, sz, f);
    fclose(f);
    env->ReleaseStringUTFChars(jPath, path);

    bool ok = fp_retro_unserialize(buf, sz);
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeReset(
        JNIEnv *env, jclass clazz) {
    if (g_core_handle && fp_retro_reset) {
        fp_retro_reset();
    }
}

JNIEXPORT void JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeDeinit(
        JNIEnv *env, jclass clazz) {
    if (g_running) {
        g_running = false;
        pthread_join(g_emu_thread, nullptr);
    }
    if (g_core_handle && fp_retro_deinit) {
        fp_retro_deinit();
    }
    pthread_mutex_lock(&g_window_mutex);
    if (g_window) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
    pthread_mutex_unlock(&g_window_mutex);
    if (g_audio_track_obj) {
        // Note: env may be invalid here; should be called before JNI teardown
        g_audio_track_obj = nullptr;
    }
    if (g_core_handle) {
        // Keep handle open - Android doesn't like dlclose on shared libs
        // that may still have pending callbacks
        g_core_handle = nullptr;
    }
}

JNIEXPORT jint JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeGetFrameWidth(JNIEnv *, jclass) {
    return (jint)g_av_info.geometry.base_width;
}

JNIEXPORT jint JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeGetFrameHeight(JNIEnv *, jclass) {
    return (jint)g_av_info.geometry.base_height;
}

JNIEXPORT jdouble JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeGetFps(JNIEnv *, jclass) {
    return g_av_info.timing.fps > 0 ? g_av_info.timing.fps : 50.0;
}

JNIEXPORT jdouble JNICALL
Java_com_atari_launcher_emulator_EmulatorBridge_nativeGetSampleRate(JNIEnv *, jclass) {
    return g_av_info.timing.sample_rate > 0 ? g_av_info.timing.sample_rate : 22050.0;
}

} // extern "C"
