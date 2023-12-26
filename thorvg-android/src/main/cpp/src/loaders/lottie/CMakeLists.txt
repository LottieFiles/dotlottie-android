
target_sources(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/tvgLottieBuilder.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgLottieInterpolator.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgLottieLoader.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgLottieModel.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgLottieParser.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgLottieParserHandler.cpp"
        )

target_include_directories(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        )