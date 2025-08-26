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
            // Read all files (customize file types as needed)
            List<File> files = listFilesRecursively(tempDir.toFile());
            for (File file : files) {
                String content = Files.readString(file.toPath());
                // Load to vector store (replace with your actual implementation)
                loadToVectorStore(file.getName(), content);
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
}
