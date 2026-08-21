package com.aidevos.orchestrator.validationplan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 轻量 TestCatalogService：确定性扫描当前 module 的测试文件。
 *
 * Java:   src/test/java/**\/*Test.java / *Tests.java
 * Frontend: *.test.ts / *.spec.ts / *.test.js / *.spec.js
 *
 * 不扫描 target / node_modules / dist / .git；catalog 为空也正常（调用方 fallback）。
 * 不做 AST / 全仓索引。
 */
@Component
public class TestCatalogService {

	public record CatalogTest(String testId, String testName, String type, String module,
			String path, String tool) {
	}

	public static final String TYPE_BACKEND_TEST = "BACKEND_TEST";
	public static final String TYPE_FRONTEND_TEST = "FRONTEND_TEST";

	private final String workspaceRoot;

	public TestCatalogService(@Value("${aidevos.workspace.root:.}") String workspaceRoot) {
		this.workspaceRoot = workspaceRoot == null || workspaceRoot.isBlank()
			? "." : workspaceRoot;
	}

	/** module 为 workingDirectory（如 services/orchestrator）。 */
	public List<CatalogTest> scan(String module) {
		if (module == null || module.isBlank() || ".".equals(module)) {
			return List.of();
		}
		Path root = Path.of(workspaceRoot, module);
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		List<CatalogTest> tests = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(Files::isRegularFile)
				.filter(path -> !isExcluded(path))
				.forEach(path -> {
					String relative = root.relativize(path).toString().replace('\\', '/');
					if (isJavaTest(relative)) {
						tests.add(javaTest(relative, module));
					}
					else if (isFrontendTest(relative)) {
						tests.add(frontendTest(relative, module));
					}
				});
		}
		catch (IOException ignored) {
			// 扫描失败 → 空 catalog（调用方 fallback）
		}
		return List.copyOf(tests);
	}

	private boolean isExcluded(Path path) {
		for (Path part : path) {
			String name = part.toString();
			if ("target".equals(name) || "node_modules".equals(name) || "dist".equals(name)
					|| ".git".equals(name) || "build".equals(name)) {
				return true;
			}
		}
		return false;
	}

	private boolean isJavaTest(String relative) {
		return relative.contains("src/test/java/")
			&& (relative.endsWith("Test.java") || relative.endsWith("Tests.java"));
	}

	private boolean isFrontendTest(String relative) {
		return relative.endsWith(".test.ts") || relative.endsWith(".spec.ts")
			|| relative.endsWith(".test.js") || relative.endsWith(".spec.js")
			|| relative.endsWith(".test.tsx") || relative.endsWith(".spec.tsx");
	}

	private CatalogTest javaTest(String relative, String module) {
		String fileName = relative.substring(relative.lastIndexOf('/') + 1);
		String testId = relative.substring(0, relative.length() - ".java".length());
		return new CatalogTest(testId, fileName, TYPE_BACKEND_TEST, module,
			module + "/" + relative, "maven");
	}

	private CatalogTest frontendTest(String relative, String module) {
		String fileName = relative.substring(relative.lastIndexOf('/') + 1);
		String testId = relative.substring(0, relative.length()
			- (relative.endsWith(".ts") || relative.endsWith(".js") ? 3 : 4));
		return new CatalogTest(testId, fileName, TYPE_FRONTEND_TEST, module,
			module + "/" + relative, "npm");
	}
}
