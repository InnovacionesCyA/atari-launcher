cmake_minimum_required(VERSION 3.22.1)
project("atarilauncher")

# Import the prebuilt atari800 libretro core
add_library(atari800_libretro SHARED IMPORTED)
set_target_properties(atari800_libretro PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/atari800_libretro_android.so
)

# Build the JNI bridge
add_library(
    atarilauncher_jni
    SHARED
    libretro_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(EGL-lib EGL)
find_library(GLESv2-lib GLESv2)
find_library(OpenSLES-lib OpenSLES)

target_link_libraries(
    atarilauncher_jni
    atari800_libretro
    ${log-lib}
    ${android-lib}
    ${EGL-lib}
    ${GLESv2-lib}
    ${OpenSLES-lib}
)

target_include_directories(atarilauncher_jni PRIVATE
    ${CMAKE_SOURCE_DIR}
)
