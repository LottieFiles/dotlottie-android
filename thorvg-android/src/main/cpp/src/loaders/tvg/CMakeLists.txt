
target_sources(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/tvgTvgBinInterpreter.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgTvgLoader.cpp"
        )

target_include_directories(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        )