target_sources(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwFill.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwImage.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwMath.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwMemPool.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwRaster.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwRenderer.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwRle.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwShape.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwStroke.cpp"
        )

target_include_directories(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        )