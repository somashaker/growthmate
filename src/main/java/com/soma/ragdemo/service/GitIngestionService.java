package com.soma.ragdemo.service;

import org.springframework.ai.document.Document;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitIngestionService {

    private static final Logger log = LoggerFactory.getLogger(GitIngestionService.class);
    private final VectorStore vectorStore;

    public GitIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Clones the given Git repository to a temporary directory, reads all files, and loads their contents to a vector store.
     * @param repoUrl The URL of the Git repository to clone.
     * @throws IOException
     * @throws GitAPIException
     */
    public void ingestFromGitRepo(String repoUrl) throws IOException, GitAPIException {
        // Clone repo to temp dir
        Path tempDir = Files.createTempDirectory("git-ingest-");
        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(tempDir.toFile())
                .call()) {
            // Only process text files
            List<File> files = listFilesRecursively(tempDir.toFile());
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".csv") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".html") || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".pdf")) {
                    try {
                        String content = Files.readString(file.toPath());
                        loadToVectorStore(file.getName(), content);
                    } catch (IOException e) {
                        log.warn("Skipping file due to read error: {}", file.getAbsolutePath());
                    }
                } else {
                    log.debug("Skipping non-text file: {}", file.getAbsolutePath());
                }
            }
        } finally {
            // Optionally delete tempDir after processing
            deleteDirectoryRecursively(tempDir);
        }
    }

    private List<File> listFilesRecursively(File dir) {
        List<File> files = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    files.addAll(listFilesRecursively(entry));
                } else {
                    files.add(entry);
                }
            }
        }
        return files;
    }

    private void loadToVectorStore(String fileName, String content) {
        // Handle PDF files differently
        System.out.println("Loaded to vector store: " + fileName);
        TextSplitter textSplitter = new TokenTextSplitter();
        if (fileName.toLowerCase().endsWith(".pdf")) {
            // For PDFs, use the file path
            var pdfReader = new PagePdfDocumentReader(fileName);
            vectorStore.accept(textSplitter.apply(pdfReader.get()));
        } else {
            // For other files, treat as plain text
            Document doc = new Document(content);
            vectorStore.accept(textSplitter.apply(List.of(doc)));
        }
        log.info("VectorStore Loaded with data!");
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // delete children first
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }

    public void ingestMultipleRepos(List<String> repoUrls) {
        int threads = Math.min(16, repoUrls.size());
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (String url : repoUrls) {
            executor.submit(() -> {
                try {
                    ingestFromGitRepo(url);
                } catch (Exception e) {
                    log.error("Failed to ingest repo: {}", url, e);
                }
            });
        }
        executor.shutdown();
        //put below logic tin while if you need to wait till all tasks are completed
        //while (!executor.isTerminated()) {
        try {
            executor.awaitTermination(1, java.util.concurrent.TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
