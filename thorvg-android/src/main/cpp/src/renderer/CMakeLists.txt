add_subdirectory(sw_engine)

target_sources(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/tvgAnimation.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgCanvas.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgFill.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgInitializer.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgLoader.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgPaint.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgPicture.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgRender.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgScene.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgShape.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgSwCanvas.cpp"
        "${CMAKE_CURRENT_LIST_DIR}/tvgTaskScheduler.cpp"
        )

target_include_directories(${CMAKE_PROJECT_NAME}
        PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        )