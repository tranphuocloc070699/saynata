import { Authenticated, Refine } from "@refinedev/core";
import routerProvider, { CatchAllNavigate, NavigateToResource } from "@refinedev/react-router";
import { BrowserRouter, Outlet, Route, Routes } from "react-router";
import { Layout } from "@/components/Layout";
import { LoginPage } from "@/pages/LoginPage";
import { MediaPage } from "@/pages/MediaPage";
import { SentencesPage } from "@/pages/SentencesPage";
import { WordsPage } from "@/pages/WordsPage";
import { authProvider } from "@/providers/authProvider";
import { dataProvider } from "@/providers/dataProvider";

export default function App() {
  return (
    <BrowserRouter>
      <Refine
        dataProvider={dataProvider}
        authProvider={authProvider}
        routerProvider={routerProvider}
        resources={[
          { name: "words", list: "/words" },
          { name: "sentences", list: "/sentences" },
        ]}
        options={{ disableTelemetry: true, syncWithLocation: false }}
      >
        <Routes>
          <Route
            element={
              <Authenticated key="admin" fallback={<CatchAllNavigate to="/login" />}>
                <Layout />
              </Authenticated>
            }
          >
            <Route index element={<NavigateToResource resource="words" />} />
            <Route path="/words" element={<WordsPage />} />
            <Route path="/sentences" element={<SentencesPage />} />
            <Route path="/media" element={<MediaPage />} />
          </Route>
          <Route
            element={
              <Authenticated key="public" fallback={<Outlet />}>
                <NavigateToResource resource="words" />
              </Authenticated>
            }
          >
            <Route path="/login" element={<LoginPage />} />
          </Route>
        </Routes>
      </Refine>
    </BrowserRouter>
  );
}
