import { afterEach, describe, expect, it, vi } from "vitest";
import { createDataProvider, httpClient } from "@/providers/dataProvider";

const provider = createDataProvider("https://api.test/api/admin");

afterEach(() => vi.restoreAllMocks());

describe("data provider", () => {
  it("sends the login cookie with every request", () => {
    expect(httpClient.defaults.withCredentials).toBe(true);
  });

  it("builds the list URL with paging, sort and filters, and reads X-Total-Count", async () => {
    const get = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue({ data: [{ id: 1 }], headers: { "x-total-count": "57" } });

    const result = await provider.getList({
      resource: "words",
      pagination: { currentPage: 2, pageSize: 20 },
      sorters: [{ field: "createdAt", order: "desc" }],
      filters: [
        { field: "q", operator: "contains", value: "run" },
        { field: "needs_review", operator: "eq", value: true },
      ],
    });

    expect(get).toHaveBeenCalledWith(
      "https://api.test/api/admin/words?_end=40&_order=desc&_sort=createdAt&_start=20&needs_review=true&q=run",
      expect.anything(),
    );
    expect(result.total).toBe(57);
    expect(result.data).toEqual([{ id: 1 }]);
  });

  it("gets one word by id", async () => {
    const get = vi.spyOn(httpClient, "get").mockResolvedValue({ data: { id: 7 }, headers: {} });
    await provider.getOne({ resource: "words", id: 7 });
    expect(get).toHaveBeenCalledWith("https://api.test/api/admin/words/7", expect.anything());
  });

  it("saves an entry with PUT when meta.method is put", async () => {
    const put = vi.spyOn(httpClient, "put").mockResolvedValue({ data: { id: 7 }, headers: {} });
    const entry = { senses: [] };
    await provider.update({ resource: "words", id: 7, variables: entry, meta: { method: "put" } });
    expect(put).toHaveBeenCalledWith("https://api.test/api/admin/words/7", entry, expect.anything());
  });

  it("regenerates (DELETE) a sentence", async () => {
    const del = vi.spyOn(httpClient, "delete").mockResolvedValue({ data: {}, headers: {} });
    await provider.deleteOne({ resource: "sentences", id: 3 });
    expect(del).toHaveBeenCalledWith("https://api.test/api/admin/sentences/3", expect.anything());
  });
});
