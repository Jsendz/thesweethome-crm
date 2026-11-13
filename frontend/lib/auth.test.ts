import { fetchMe, login, logout, getCsrfToken } from "./auth";

describe("getCsrfToken", () => {
  it("reads the XSRF-TOKEN cookie out of document.cookie", () => {
    Object.defineProperty(document, "cookie", { value: "other=1; XSRF-TOKEN=abc123; more=2", writable: true });
    expect(getCsrfToken()).toBe("abc123");
  });

  it("returns null when the cookie isn't present", () => {
    Object.defineProperty(document, "cookie", { value: "other=1", writable: true });
    expect(getCsrfToken()).toBeNull();
  });
});

describe("fetchMe", () => {
  it("returns the current user on success", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: "1", email: "a@b.com", fullName: "A B", role: "ADMIN" }),
    }) as jest.Mock;
    expect(await fetchMe()).toEqual({ id: "1", email: "a@b.com", fullName: "A B", role: "ADMIN" });
  });

  it("returns null when there's no session", async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 401 }) as jest.Mock;
    expect(await fetchMe()).toBeNull();
  });
});

describe("login", () => {
  it("posts form-urlencoded credentials with the CSRF header and returns true on success", async () => {
    Object.defineProperty(document, "cookie", { value: "XSRF-TOKEN=tok", writable: true });
    const fetchMock = jest.fn().mockResolvedValue({ ok: true });
    global.fetch = fetchMock as jest.Mock;

    const result = await login("a@b.com", "pw");

    expect(result).toBe(true);
    const [, options] = fetchMock.mock.calls[0];
    expect(options.credentials).toBe("include");
    expect(options.headers["X-XSRF-TOKEN"]).toBe("tok");
    expect(options.body).toContain("email=a%40b.com");
  });

  it("returns false on failure", async () => {
    Object.defineProperty(document, "cookie", { value: "XSRF-TOKEN=tok", writable: true });
    global.fetch = jest.fn().mockResolvedValue({ ok: false }) as jest.Mock;
    expect(await login("a@b.com", "wrong")).toBe(false);
  });
});

describe("logout", () => {
  it("posts to the logout endpoint with the CSRF header", async () => {
    Object.defineProperty(document, "cookie", { value: "XSRF-TOKEN=tok", writable: true });
    const fetchMock = jest.fn().mockResolvedValue({ ok: true });
    global.fetch = fetchMock as jest.Mock;

    await logout();

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/auth/logout");
    expect(options.headers["X-XSRF-TOKEN"]).toBe("tok");
  });
});
