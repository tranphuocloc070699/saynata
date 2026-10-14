import simpleRestProvider from "@refinedev/simple-rest";
import axios from "axios";
import { ADMIN_API_URL } from "./config";

// Refine's simple-rest provider, with one change: axios sends the login cookie (withCredentials).
// getList sends _start, _end, _sort, _order and filters; the total comes from X-Total-Count.
export const httpClient = axios.create({ withCredentials: true });

// Keep the error shape Refine expects (message + statusCode), like simple-rest does itself.
httpClient.interceptors.response.use(
  (response) => response,
  (error) =>
    Promise.reject({
      ...error,
      message: error.response?.data?.message ?? error.message,
      statusCode: error.response?.status,
    }),
);

export function createDataProvider(apiUrl: string = ADMIN_API_URL) {
  return simpleRestProvider(apiUrl, httpClient);
}

export const dataProvider = createDataProvider();
