package com.moneylytics.api.adapter.input.web

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
@RequestMapping("/local/environment")
class EnvironmentController {
    @GetMapping
    fun environment(): Map<String, String> = System.getenv().toSortedMap()
}
