#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {

constexpr const char* kLogTag = "DendyLibretro";

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

constexpr unsigned RETRO_DEVICE_JOYPAD = 1;

constexpr unsigned RETRO_DEVICE_ID_JOYPAD_B = 0;
constexpr unsigned RETRO_DEVICE_ID_JOYPAD_SELECT = 2;
constexpr unsigned RETRO_DEVICE_ID_JOYPAD_START = 3;
constexpr unsigned RETRO_DEVICE_ID_JOYPAD_UP = 4;
constexpr unsigned RETRO_DEVICE_ID_JOYPAD_DOWN = 5;
constexpr unsigned RETRO_DEVICE_ID_JOYPAD_LEFT = 6;
constexpr unsigned RETRO_DEVICE_ID_JOYPAD_RIGHT = 7;
constexpr unsigned RETRO_DEVICE_ID_JOYPAD_A = 8;

constexpr unsigned RETRO_ENVIRONMENT_GET_CAN_DUPE = 3;
constexpr unsigned RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY = 9;
constexpr unsigned RETRO_ENVIRONMENT_SET_PIXEL_FORMAT = 10;
constexpr unsigned RETRO_ENVIRONMENT_GET_VARIABLE = 15;
constexpr unsigned RETRO_ENVIRONMENT_SET_VARIABLES = 16;
constexpr unsigned RETRO_ENVIRONMENT_GET_LOG_INTERFACE = 27;
constexpr unsigned RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY = 31;
constexpr unsigned RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO = 32;
constexpr unsigned RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS = 34;
constexpr unsigned RETRO_ENVIRONMENT_SET_CONTROLLER_INFO = 35;
constexpr unsigned RETRO_ENVIRONMENT_SET_GEOMETRY = 37;
constexpr unsigned RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE = 17;
constexpr unsigned RETRO_ENVIRONMENT_SET_MEMORY_MAPS = 36;

enum retro_pixel_format {
    RETRO_PIXEL_FORMAT_0RGB1555 = 0,
    RETRO_PIXEL_FORMAT_XRGB8888 = 1,
    RETRO_PIXEL_FORMAT_RGB565 = 2,
};

struct retro_game_info {
    const char* path;
    const void* data;
    size_t size;
    const char* meta;
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

struct retro_system_info {
    const char* library_name;
    const char* library_version;
    const char* valid_extensions;
    bool need_fullpath;
    bool block_extract;
};

enum retro_log_level {
    RETRO_LOG_DEBUG = 0,
    RETRO_LOG_INFO = 1,
    RETRO_LOG_WARN = 2,
    RETRO_LOG_ERROR = 3,
};

using retro_log_printf_t = void (*)(enum retro_log_level, const char*, ...);

struct retro_log_callback {
    retro_log_printf_t log;
};

struct retro_variable {
    const char* key;
    const char* value;
};

using retro_environment_t = bool (*)(unsigned, void*);
using retro_video_refresh_t = void (*)(const void*, unsigned, unsigned, size_t);
using retro_audio_sample_t = void (*)(int16_t, int16_t);
using retro_audio_sample_batch_t = size_t (*)(const int16_t*, size_t);
using retro_input_poll_t = void (*)();
using retro_input_state_t = int16_t (*)(unsigned, unsigned, unsigned, unsigned);

using retro_set_environment_t = void (*)(retro_environment_t);
using retro_set_video_refresh_t = void (*)(retro_video_refresh_t);
using retro_set_audio_sample_t = void (*)(retro_audio_sample_t);
using retro_set_audio_sample_batch_t = void (*)(retro_audio_sample_batch_t);
using retro_set_input_poll_t = void (*)(retro_input_poll_t);
using retro_set_input_state_t = void (*)(retro_input_state_t);
using retro_init_t = void (*)();
using retro_deinit_t = void (*)();
using retro_get_system_info_t = void (*)(retro_system_info*);
using retro_get_system_av_info_t = void (*)(retro_system_av_info*);
using retro_load_game_t = bool (*)(const retro_game_info*);
using retro_unload_game_t = void (*)();
using retro_run_t = void (*)();
using retro_serialize_size_t = size_t (*)();
using retro_serialize_t = bool (*)(void*, size_t);
using retro_unserialize_t = bool (*)(const void*, size_t);
using retro_set_controller_port_device_t = void (*)(unsigned, unsigned);

struct AudioRingBuffer {
    explicit AudioRingBuffer(size_t capacity)
        : data(capacity) {}

    void pushSamples(const int16_t* source, size_t sample_count) {
        std::lock_guard<std::mutex> lock(mutex);
        for (size_t i = 0; i < sample_count; ++i) {
            if (count == data.size()) {
                head = (head + 1) % data.size();
                --count;
            }
            data[tail] = source[i];
            tail = (tail + 1) % data.size();
            ++count;
        }
    }

    size_t popSamples(int16_t* target, size_t sample_count) {
        std::lock_guard<std::mutex> lock(mutex);
        size_t read = 0;
        while (read < sample_count && count > 0) {
            target[read++] = data[head];
            head = (head + 1) % data.size();
            --count;
        }
        return read;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex);
        head = 0;
        tail = 0;
        count = 0;
    }

    std::vector<int16_t> data;
    size_t head = 0;
    size_t tail = 0;
    size_t count = 0;
    std::mutex mutex;
};

struct Backend {
    void* library_handle = nullptr;

    retro_set_environment_t retro_set_environment = nullptr;
    retro_set_video_refresh_t retro_set_video_refresh = nullptr;
    retro_set_audio_sample_t retro_set_audio_sample = nullptr;
    retro_set_audio_sample_batch_t retro_set_audio_sample_batch = nullptr;
    retro_set_input_poll_t retro_set_input_poll = nullptr;
    retro_set_input_state_t retro_set_input_state = nullptr;
    retro_init_t retro_init = nullptr;
    retro_deinit_t retro_deinit = nullptr;
    retro_get_system_info_t retro_get_system_info = nullptr;
    retro_get_system_av_info_t retro_get_system_av_info = nullptr;
    retro_load_game_t retro_load_game = nullptr;
    retro_unload_game_t retro_unload_game = nullptr;
    retro_run_t retro_run = nullptr;
    retro_serialize_size_t retro_serialize_size = nullptr;
    retro_serialize_t retro_serialize = nullptr;
    retro_unserialize_t retro_unserialize = nullptr;
    retro_set_controller_port_device_t retro_set_controller_port_device = nullptr;

    std::string system_dir;
    std::string save_dir;
    std::string state_dir;

    retro_system_info system_info{};
    retro_system_av_info av_info{};
    retro_pixel_format pixel_format = RETRO_PIXEL_FORMAT_XRGB8888;

    std::vector<uint8_t> latest_frame_rgba;
    unsigned frame_width = 256;
    unsigned frame_height = 240;
    std::mutex frame_mutex;

    AudioRingBuffer audio_buffer{48000 * 4};

    std::vector<uint8_t> rom_bytes;
    bool game_loaded = false;
    std::atomic<int> input_mask{0};
};

void RetroLogCallback(retro_log_level level, const char* fmt, ...) {
    if (fmt == nullptr) {
        return;
    }

    int android_priority = ANDROID_LOG_INFO;
    switch (level) {
        case RETRO_LOG_DEBUG:
            android_priority = ANDROID_LOG_DEBUG;
            break;
        case RETRO_LOG_INFO:
            android_priority = ANDROID_LOG_INFO;
            break;
        case RETRO_LOG_WARN:
            android_priority = ANDROID_LOG_WARN;
            break;
        case RETRO_LOG_ERROR:
            android_priority = ANDROID_LOG_ERROR;
            break;
    }

    va_list args;
    va_start(args, fmt);
    __android_log_vprint(android_priority, kLogTag, fmt, args);
    va_end(args);
}

Backend* g_active_backend = nullptr;

template <typename T>
T ResolveSymbol(void* handle, const char* symbol_name, bool required = true) {
    auto symbol = reinterpret_cast<T>(dlsym(handle, symbol_name));
    if (required && symbol == nullptr) {
        LOGE("Missing required symbol: %s", symbol_name);
    }
    return symbol;
}

bool EnvironmentCallback(unsigned cmd, void* data) {
    auto* backend = g_active_backend;
    if (backend == nullptr) {
        return false;
    }

    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data != nullptr) {
                *static_cast<bool*>(data) = true;
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            if (data != nullptr) {
                *static_cast<const char**>(data) = backend->system_dir.c_str();
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            if (data != nullptr) {
                auto* callback = static_cast<retro_log_callback*>(data);
                callback->log = RetroLogCallback;
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_GET_VARIABLE:
            if (data != nullptr) {
                auto* variable = static_cast<retro_variable*>(data);
                variable->value = nullptr;
                return false;
            }
            return false;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            if (data != nullptr) {
                *static_cast<bool*>(data) = false;
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data != nullptr) {
                *static_cast<const char**>(data) = backend->save_dir.c_str();
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (data != nullptr) {
                backend->pixel_format = *static_cast<const retro_pixel_format*>(data);
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
            if (data != nullptr) {
                backend->av_info = *static_cast<const retro_system_av_info*>(data);
                backend->frame_width = backend->av_info.geometry.base_width;
                backend->frame_height = backend->av_info.geometry.base_height;
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            if (data != nullptr) {
                backend->av_info.geometry = *static_cast<const retro_game_geometry*>(data);
                backend->frame_width = backend->av_info.geometry.base_width;
                backend->frame_height = backend->av_info.geometry.base_height;
                return true;
            }
            return false;
        default:
            return false;
    }
}

void VideoRefreshCallback(const void* data, unsigned width, unsigned height, size_t pitch) {
    auto* backend = g_active_backend;
    if (backend == nullptr || data == nullptr || width == 0 || height == 0) {
        return;
    }

    std::vector<uint8_t> rgba(width * height * 4);
    const auto* source = static_cast<const uint8_t*>(data);

    switch (backend->pixel_format) {
        case RETRO_PIXEL_FORMAT_XRGB8888: {
            for (unsigned y = 0; y < height; ++y) {
                const auto* row = reinterpret_cast<const uint32_t*>(source + (y * pitch));
                for (unsigned x = 0; x < width; ++x) {
                    const uint32_t pixel = row[x];
                    const size_t offset = static_cast<size_t>(y * width + x) * 4;
                    rgba[offset + 0] = static_cast<uint8_t>((pixel >> 16) & 0xFF);
                    rgba[offset + 1] = static_cast<uint8_t>((pixel >> 8) & 0xFF);
                    rgba[offset + 2] = static_cast<uint8_t>(pixel & 0xFF);
                    rgba[offset + 3] = 0xFF;
                }
            }
            break;
        }
        case RETRO_PIXEL_FORMAT_RGB565: {
            for (unsigned y = 0; y < height; ++y) {
                const auto* row = reinterpret_cast<const uint16_t*>(source + (y * pitch));
                for (unsigned x = 0; x < width; ++x) {
                    const uint16_t pixel = row[x];
                    const size_t offset = static_cast<size_t>(y * width + x) * 4;
                    rgba[offset + 0] = static_cast<uint8_t>(((pixel >> 11) & 0x1F) * 255 / 31);
                    rgba[offset + 1] = static_cast<uint8_t>(((pixel >> 5) & 0x3F) * 255 / 63);
                    rgba[offset + 2] = static_cast<uint8_t>((pixel & 0x1F) * 255 / 31);
                    rgba[offset + 3] = 0xFF;
                }
            }
            break;
        }
        case RETRO_PIXEL_FORMAT_0RGB1555:
        default: {
            for (unsigned y = 0; y < height; ++y) {
                const auto* row = reinterpret_cast<const uint16_t*>(source + (y * pitch));
                for (unsigned x = 0; x < width; ++x) {
                    const uint16_t pixel = row[x];
                    const size_t offset = static_cast<size_t>(y * width + x) * 4;
                    rgba[offset + 0] = static_cast<uint8_t>(((pixel >> 10) & 0x1F) * 255 / 31);
                    rgba[offset + 1] = static_cast<uint8_t>(((pixel >> 5) & 0x1F) * 255 / 31);
                    rgba[offset + 2] = static_cast<uint8_t>((pixel & 0x1F) * 255 / 31);
                    rgba[offset + 3] = 0xFF;
                }
            }
            break;
        }
    }

    std::lock_guard<std::mutex> lock(backend->frame_mutex);
    backend->frame_width = width;
    backend->frame_height = height;
    backend->latest_frame_rgba = std::move(rgba);
}

void AudioSampleCallback(int16_t left, int16_t right) {
    auto* backend = g_active_backend;
    if (backend == nullptr) {
        return;
    }
    const int16_t sample[2] = {left, right};
    backend->audio_buffer.pushSamples(sample, 2);
}

size_t AudioSampleBatchCallback(const int16_t* data, size_t frames) {
    auto* backend = g_active_backend;
    if (backend == nullptr || data == nullptr || frames == 0) {
        return 0;
    }
    backend->audio_buffer.pushSamples(data, frames * 2);
    return frames;
}

void InputPollCallback() {}

int16_t InputStateCallback(unsigned port, unsigned device, unsigned index, unsigned id) {
    auto* backend = g_active_backend;
    if (backend == nullptr || port != 0 || device != RETRO_DEVICE_JOYPAD || index != 0) {
        return 0;
    }
    const int mask = backend->input_mask.load();
    const int bit = 1 << id;
    return (mask & bit) != 0 ? 1 : 0;
}

Backend* FromHandle(jlong handle) {
    return reinterpret_cast<Backend*>(handle);
}

void UnloadCore(Backend* backend) {
    if (backend == nullptr) {
        return;
    }

    g_active_backend = backend;
    if (backend->game_loaded && backend->retro_unload_game != nullptr) {
        backend->retro_unload_game();
        backend->game_loaded = false;
    }
    if (backend->retro_deinit != nullptr) {
        backend->retro_deinit();
    }
    g_active_backend = nullptr;

    if (backend->library_handle != nullptr) {
        dlclose(backend->library_handle);
        backend->library_handle = nullptr;
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeCreateBackend(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new Backend());
}

extern "C" JNIEXPORT void JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeDestroyBackend(
    JNIEnv*,
    jobject,
    jlong handle
) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr) {
        return;
    }
    UnloadCore(backend);
    delete backend;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeLoadCore(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring core_path,
    jstring system_dir,
    jstring save_dir,
    jstring state_dir
) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr) {
        return JNI_FALSE;
    }

    UnloadCore(backend);

    const char* core_path_chars = env->GetStringUTFChars(core_path, nullptr);
    const char* system_dir_chars = env->GetStringUTFChars(system_dir, nullptr);
    const char* save_dir_chars = env->GetStringUTFChars(save_dir, nullptr);
    const char* state_dir_chars = env->GetStringUTFChars(state_dir, nullptr);

    backend->system_dir = system_dir_chars != nullptr ? system_dir_chars : "";
    backend->save_dir = save_dir_chars != nullptr ? save_dir_chars : "";
    backend->state_dir = state_dir_chars != nullptr ? state_dir_chars : "";

    backend->library_handle = dlopen(core_path_chars, RTLD_NOW);

    env->ReleaseStringUTFChars(core_path, core_path_chars);
    env->ReleaseStringUTFChars(system_dir, system_dir_chars);
    env->ReleaseStringUTFChars(save_dir, save_dir_chars);
    env->ReleaseStringUTFChars(state_dir, state_dir_chars);

    if (backend->library_handle == nullptr) {
        LOGE("dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }

    backend->retro_set_environment =
        ResolveSymbol<retro_set_environment_t>(backend->library_handle, "retro_set_environment");
    backend->retro_set_video_refresh =
        ResolveSymbol<retro_set_video_refresh_t>(backend->library_handle, "retro_set_video_refresh");
    backend->retro_set_audio_sample =
        ResolveSymbol<retro_set_audio_sample_t>(backend->library_handle, "retro_set_audio_sample");
    backend->retro_set_audio_sample_batch = ResolveSymbol<retro_set_audio_sample_batch_t>(
        backend->library_handle,
        "retro_set_audio_sample_batch"
    );
    backend->retro_set_input_poll =
        ResolveSymbol<retro_set_input_poll_t>(backend->library_handle, "retro_set_input_poll");
    backend->retro_set_input_state =
        ResolveSymbol<retro_set_input_state_t>(backend->library_handle, "retro_set_input_state");
    backend->retro_init = ResolveSymbol<retro_init_t>(backend->library_handle, "retro_init");
    backend->retro_deinit = ResolveSymbol<retro_deinit_t>(backend->library_handle, "retro_deinit");
    backend->retro_get_system_info =
        ResolveSymbol<retro_get_system_info_t>(backend->library_handle, "retro_get_system_info");
    backend->retro_get_system_av_info = ResolveSymbol<retro_get_system_av_info_t>(
        backend->library_handle,
        "retro_get_system_av_info"
    );
    backend->retro_load_game =
        ResolveSymbol<retro_load_game_t>(backend->library_handle, "retro_load_game");
    backend->retro_unload_game =
        ResolveSymbol<retro_unload_game_t>(backend->library_handle, "retro_unload_game", false);
    backend->retro_run = ResolveSymbol<retro_run_t>(backend->library_handle, "retro_run");
    backend->retro_serialize_size = ResolveSymbol<retro_serialize_size_t>(
        backend->library_handle,
        "retro_serialize_size"
    );
    backend->retro_serialize =
        ResolveSymbol<retro_serialize_t>(backend->library_handle, "retro_serialize");
    backend->retro_unserialize =
        ResolveSymbol<retro_unserialize_t>(backend->library_handle, "retro_unserialize");
    backend->retro_set_controller_port_device =
        ResolveSymbol<retro_set_controller_port_device_t>(
            backend->library_handle,
            "retro_set_controller_port_device",
            false
        );

    if (backend->retro_set_environment == nullptr ||
        backend->retro_set_video_refresh == nullptr ||
        backend->retro_set_audio_sample == nullptr ||
        backend->retro_set_audio_sample_batch == nullptr ||
        backend->retro_set_input_poll == nullptr ||
        backend->retro_set_input_state == nullptr ||
        backend->retro_init == nullptr ||
        backend->retro_deinit == nullptr ||
        backend->retro_get_system_info == nullptr ||
        backend->retro_get_system_av_info == nullptr ||
        backend->retro_load_game == nullptr ||
        backend->retro_run == nullptr ||
        backend->retro_serialize_size == nullptr ||
        backend->retro_serialize == nullptr ||
        backend->retro_unserialize == nullptr) {
        UnloadCore(backend);
        return JNI_FALSE;
    }

    g_active_backend = backend;
    backend->retro_set_environment(EnvironmentCallback);
    backend->retro_init();
    backend->retro_set_video_refresh(VideoRefreshCallback);
    backend->retro_set_audio_sample(AudioSampleCallback);
    backend->retro_set_audio_sample_batch(AudioSampleBatchCallback);
    backend->retro_set_input_poll(InputPollCallback);
    backend->retro_set_input_state(InputStateCallback);
    backend->retro_get_system_info(&backend->system_info);
    if (backend->retro_set_controller_port_device != nullptr) {
        backend->retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    }
    g_active_backend = nullptr;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeLoadRom(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring rom_path
) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr || backend->library_handle == nullptr) {
        return JNI_FALSE;
    }

    const char* rom_path_chars = env->GetStringUTFChars(rom_path, nullptr);
    std::string rom_path_string = rom_path_chars != nullptr ? rom_path_chars : "";
    env->ReleaseStringUTFChars(rom_path, rom_path_chars);

    FILE* file = fopen(rom_path_string.c_str(), "rb");
    if (file == nullptr) {
        LOGE("Failed to open ROM: %s", rom_path_string.c_str());
        return JNI_FALSE;
    }
    fseek(file, 0, SEEK_END);
    long file_size = ftell(file);
    fseek(file, 0, SEEK_SET);
    backend->rom_bytes.resize(file_size > 0 ? static_cast<size_t>(file_size) : 0);
    if (!backend->rom_bytes.empty()) {
        fread(backend->rom_bytes.data(), 1, backend->rom_bytes.size(), file);
    }
    fclose(file);

    retro_game_info info{};
    info.path = rom_path_string.c_str();
    if (!backend->system_info.need_fullpath) {
        info.data = backend->rom_bytes.data();
        info.size = backend->rom_bytes.size();
    }

    g_active_backend = backend;
    if (!backend->retro_load_game(&info)) {
        g_active_backend = nullptr;
        LOGE("retro_load_game failed: %s", rom_path_string.c_str());
        return JNI_FALSE;
    }
    backend->retro_get_system_av_info(&backend->av_info);
    backend->frame_width = backend->av_info.geometry.base_width;
    backend->frame_height = backend->av_info.geometry.base_height;
    backend->game_loaded = true;
    g_active_backend = nullptr;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeRunFrame(JNIEnv*, jobject, jlong handle) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr || !backend->game_loaded || backend->retro_run == nullptr) {
        return JNI_FALSE;
    }
    g_active_backend = backend;
    backend->retro_run();
    g_active_backend = nullptr;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeGetFrameWidth(JNIEnv*, jobject, jlong handle) {
    auto* backend = FromHandle(handle);
    return backend != nullptr ? static_cast<jint>(backend->frame_width) : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeGetFrameHeight(JNIEnv*, jobject, jlong handle) {
    auto* backend = FromHandle(handle);
    return backend != nullptr ? static_cast<jint>(backend->frame_height) : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeGetSampleRate(JNIEnv*, jobject, jlong handle) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr) {
        return 0;
    }
    return static_cast<jint>(backend->av_info.timing.sample_rate);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeGetFrameRate(JNIEnv*, jobject, jlong handle) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr) {
        return 0.0;
    }
    return static_cast<jdouble>(backend->av_info.timing.fps);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeCopyFrame(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject buffer
) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr || buffer == nullptr) {
        return JNI_FALSE;
    }
    auto* target = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (target == nullptr) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(backend->frame_mutex);
    if (backend->latest_frame_rgba.empty()) {
        return JNI_FALSE;
    }
    std::memcpy(target, backend->latest_frame_rgba.data(), backend->latest_frame_rgba.size());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeReadAudio(
    JNIEnv* env,
    jobject,
    jlong handle,
    jshortArray target,
    jint max_samples
) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr || target == nullptr || max_samples <= 0) {
        return 0;
    }
    const jsize requested = std::min(max_samples, env->GetArrayLength(target));
    std::vector<int16_t> samples(requested);
    const size_t read = backend->audio_buffer.popSamples(samples.data(), static_cast<size_t>(requested));
    if (read == 0) {
        return 0;
    }
    env->SetShortArrayRegion(target, 0, static_cast<jsize>(read), samples.data());
    return static_cast<jint>(read);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeSetInputMask(
    JNIEnv*,
    jobject,
    jlong handle,
    jint mask
) {
    auto* backend = FromHandle(handle);
    if (backend != nullptr) {
        backend->input_mask.store(mask);
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeSerialize(JNIEnv* env, jobject, jlong handle) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr || backend->retro_serialize_size == nullptr || backend->retro_serialize == nullptr) {
        return nullptr;
    }
    const size_t size = backend->retro_serialize_size();
    if (size == 0) {
        return nullptr;
    }
    std::vector<uint8_t> state(size);
    if (!backend->retro_serialize(state.data(), state.size())) {
        return nullptr;
    }
    auto result = env->NewByteArray(static_cast<jsize>(state.size()));
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(state.size()),
        reinterpret_cast<const jbyte*>(state.data())
    );
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dendy_core_emulation_NativeBindings_nativeUnserialize(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray data
) {
    auto* backend = FromHandle(handle);
    if (backend == nullptr || data == nullptr || backend->retro_unserialize == nullptr) {
        return JNI_FALSE;
    }
    const jsize length = env->GetArrayLength(data);
    std::vector<uint8_t> state(static_cast<size_t>(length));
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte*>(state.data()));
    return backend->retro_unserialize(state.data(), state.size()) ? JNI_TRUE : JNI_FALSE;
}
