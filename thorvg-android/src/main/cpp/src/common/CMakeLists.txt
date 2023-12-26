
target_sources(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/tvgBezier.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgCompressor.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgMath.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgStr.cpp"
        )

target_include_directories(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        )