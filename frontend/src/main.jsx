import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import SiteLayout from "./layout/SiteLayout.jsx";
import HomePage from "./home/HomePage.jsx";
import "./styles.css";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <SiteLayout><HomePage /></SiteLayout>
  </StrictMode>,
);
