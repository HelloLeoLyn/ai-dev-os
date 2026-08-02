package com.aidevos.orchestrator.executor.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UntrackedArtifactCollector {

	private final ArtifactContentLimiter contentLimiter;
	private final int maxFileBytes;

	public UntrackedArtifactCollector(ArtifactContentLimiter contentLimiter,
			@Value("${coding.artifacts.max-untracked-file-bytes:262144}") int maxFileBytes) {
		if (maxFileBytes < 1 || maxFileBytes > Integer.MAX_VALUE - 4) {
			throw new IllegalArgumentException("Untracked artifact max file bytes must be positive");
		}
		this.contentLimiter = contentLimiter;
		this.maxFileBytes = maxFileBytes;
	}

	public List<ExecutionArtifact> collect(String workspace, List<String> untrackedFiles) {
		List<String> paths = untrackedFiles == null ? List.of() : List.copyOf(untrackedFiles);
		List<ExecutionArtifact> artifacts = new ArrayList<>();
		ExecutionArtifact index = textArtifact("git-untracked-files", "untracked-files.txt",
			String.join("\n", paths));
		index.getMetadata().put("count", paths.size());
		index.getMetadata().put("paths", paths);
		List<String> skippedPaths = new ArrayList<>();
		artifacts.add(index);

		Path root = realDirectory(workspace);
		for (String relativePath : paths) {
			ExecutionArtifact artifact = collectFile(root, relativePath);
			if (artifact == null) {
				skippedPaths.add(relativePath);
			}
			else {
				artifacts.add(artifact);
			}
		}
		index.getMetadata().put("skippedPaths", List.copyOf(skippedPaths));
		return List.copyOf(artifacts);
	}

	private ExecutionArtifact collectFile(Path root, String relativePath) {
		try {
			Path relative = Path.of(relativePath);
			if (relative.isAbsolute()) {
				return null;
			}
			Path candidate = root.resolve(relative).normalize();
			if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate)
					|| !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
				return null;
			}
			Path realFile = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
			if (!realFile.startsWith(root)) {
				return null;
			}
			long size = Files.size(realFile);
			byte[] bytes;
			try (InputStream input = Files.newInputStream(realFile, StandardOpenOption.READ)) {
				bytes = input.readNBytes(maxFileBytes + 4);
			}
			boolean truncated = size > maxFileBytes;
			int contentLength = Math.min(bytes.length, maxFileBytes);
			String content = decodeUtf8(bytes, contentLength, truncated);

			ExecutionArtifact artifact = new ExecutionArtifact();
			artifact.setType("git-untracked-file");
			artifact.setName(relativePath);
			artifact.getMetadata().put("path", relativePath);
			artifact.getMetadata().put("sizeBytes", size);
			artifact.getMetadata().put("truncatedBytes", truncated);
			artifact.getMetadata().put("binary", content == null);
			if (content == null) {
				artifact.setMediaType("application/octet-stream");
				artifact.setContent(null);
			}
			else {
				artifact.setMediaType("text/plain; charset=utf-8");
				contentLimiter.apply(artifact, content);
			}
			return artifact;
		}
		catch (IOException | RuntimeException exception) {
			return null;
		}
	}

	private String decodeUtf8(byte[] bytes, int length, boolean truncated) {
		if (containsNull(bytes, length)) {
			return null;
		}
		int attempts = truncated ? Math.min(3, length) : 0;
		for (int removed = 0; removed <= attempts; removed++) {
			try {
				return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes, 0, length - removed))
					.toString();
			}
			catch (CharacterCodingException exception) {
				// A truncated UTF-8 sequence may occupy up to four bytes.
			}
		}
		return null;
	}

	private boolean containsNull(byte[] bytes, int length) {
		for (int index = 0; index < length; index++) {
			if (bytes[index] == 0) {
				return true;
			}
		}
		return false;
	}

	private Path realDirectory(String workspace) {
		try {
			Path root = Path.of(workspace).toRealPath();
			if (!Files.isDirectory(root)) {
				throw new IllegalArgumentException("Workspace is not a directory: " + root);
			}
			return root;
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("Workspace does not exist: " + workspace, exception);
		}
	}

	private ExecutionArtifact textArtifact(String type, String name, String content) {
		ExecutionArtifact artifact = new ExecutionArtifact();
		artifact.setType(type);
		artifact.setName(name);
		artifact.setMediaType("text/plain; charset=utf-8");
		contentLimiter.apply(artifact, content);
		return artifact;
	}
}
