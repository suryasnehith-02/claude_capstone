import { test, expect } from "./harness.js";

function getFooter() {
  return document.querySelector("footer.site-footer");
}

test("footer: has Terms and Conditions link", () => {
  const footer = getFooter();
  expect(footer, "footer exists").ok();

  const link = footer.querySelector("a");
  expect(link, "terms link exists").ok();
  expect(link.textContent.trim()).equal("Terms and Conditions");
  expect(link.getAttribute("href")).equal("/terms.html");
  expect(link.getAttribute("target")).equal("_blank");

  const rel = link.getAttribute("rel") ?? "";
  expect(rel.includes("noopener"), "rel includes noopener").ok();
});
