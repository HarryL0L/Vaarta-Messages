# Native GIF transcoder for the standalone Gradle build. Mirrors jni/Android.bp
# and the libgif target of AOSP's external/giflib.

cmake_minimum_required(VERSION 3.22.1)
project(giftranscode C CXX)

set(GIFLIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/giflib)

add_library(gif STATIC
        ${GIFLIB_DIR}/dgif_lib.c
        ${GIFLIB_DIR}/egif_lib.c
        ${GIFLIB_DIR}/gifalloc.c
        ${GIFLIB_DIR}/gif_err.c
        ${GIFLIB_DIR}/gif_hash.c
        ${GIFLIB_DIR}/quantize.c)
target_include_directories(gif PUBLIC ${GIFLIB_DIR})
target_compile_options(gif PRIVATE
        -Wno-format -Wno-sign-compare -Wno-unused-parameter -DHAVE_REALLOCARRAY)

add_library(giftranscode SHARED GifTranscoder.cpp)
target_compile_options(giftranscode PRIVATE -Wno-unused-parameter -Wno-switch)
target_link_libraries(giftranscode gif log dl)
