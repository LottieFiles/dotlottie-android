
target_sources(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/tvgSvgCssStyle.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSvgLoader.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSvgPath.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSvgSceneBuilder.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSvgUtil.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgXmlParser.cpp"
        )

target_include_directories(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        )