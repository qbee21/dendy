cmake_minimum_required(VERSION 3.22.1)

project(dendy_libretro_bridge)

add_library(
    dendy_libretro_bridge
    SHARED
    dendy_libretro_bridge.cpp
)

find_library(log-lib log)

target_compile_features(dendy_libretro_bridge PRIVATE cxx_std_20)

target_link_libraries(
    dendy_libretro_bridge
    c++_shared
    ${log-lib}
    dl
)
