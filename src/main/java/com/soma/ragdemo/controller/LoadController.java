package com.soma.ragdemo.controller;

import com.soma.ragdemo.dto.ChatRequest;
import com.soma.ragdemo.dto.GitRepoDetailsDto;
import com.soma.ragdemo.service.GitIngestionService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class LoadController {

    @Autowired
    GitIngestionService gitService;

    @PostMapping("/load")
    public void chat(@RequestBody GitRepoDetailsDto request) throws GitAPIException, IOException {
        gitService.ingestFromGitRepo(request.getRepoName());
    }
}
