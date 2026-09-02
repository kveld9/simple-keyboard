cmake_minimum_required(VERSION 3.22.1)

project("microtransformer" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(
    -O3
    -ffast-math
    -Wall
    -Wextra
)

add_library(
    microtransformer
    SHARED
    micro_transformer.cpp
    micro_frontier_engine.cpp
    micro_transformer_jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    microtransformer
    ${log-lib}
)
