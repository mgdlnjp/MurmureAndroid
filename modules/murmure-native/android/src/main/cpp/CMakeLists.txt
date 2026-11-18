cmake_minimum_required(VERSION 3.22)

project(murmure_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# whisper.cpp v1.9.5 (archive officielle, empreinte vérifiée)
FetchContent_Declare(
  whisper
  URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz
  URL_HASH SHA256=ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# Base armv8-a : pas de +fp16 / dotprod forcés (risque SIGILL sur anciens SoC).
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)

FetchContent_MakeAvailable(whisper)

add_library(murmure_whisper SHARED murmure_jni.cpp)

target_link_libraries(murmure_whisper whisper log android)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
  target_compile_options(murmure_whisper PRIVATE -O3 -ffunction-sections -fdata-sections)
  target_link_options(murmure_whisper PRIVATE -Wl,--gc-sections)
endif ()

# Pages de 16 Ko (Android 15+)
target_link_options(murmure_whisper PRIVATE -Wl,-z,max-page-size=16384)
