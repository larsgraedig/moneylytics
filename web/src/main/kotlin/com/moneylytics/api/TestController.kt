package com.moneylytics.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController {
    @GetMapping("/")
    fun test(): ResponseEntity<String> {
        val body = "Hello, World!"
        return ResponseEntity.ok(body)
    }
}
