# JaguarClaw Website Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an independent `Next.js + MDX/Contentlayer` official website inside current repo that includes enterprise homepage, docs, blog, and feedback entry.

**Architecture:** Create a standalone subproject `jaguarclaw-website` using Next.js App Router and Contentlayer-powered local MDX content. Implement shared layout/navigation and page sections first, then wire docs/blog content routing with typed models and basic SEO metadata.

**Tech Stack:** Next.js 15 (App Router), React 19, TypeScript, Contentlayer, MDX, CSS Variables.

---

### Task 1: Bootstrap standalone website project structure

**Files:**
- Create: `jaguarclaw-website/package.json`
- Create: `jaguarclaw-website/tsconfig.json`
- Create: `jaguarclaw-website/next.config.mjs`
- Create: `jaguarclaw-website/contentlayer.config.ts`
- Create: `jaguarclaw-website/next-env.d.ts`
- Create: `jaguarclaw-website/.gitignore`
- Create: `jaguarclaw-website/README.md`

**Step 1: Write the failing test**
- Add smoke test file asserting homepage component can be imported and rendered.

**Step 2: Run test to verify it fails**
- Run: `cd jaguarclaw-website && npm test`
- Expected: FAIL because project dependencies/config are missing.

**Step 3: Write minimal implementation**
- Create package and TypeScript/Next/Contentlayer baseline configs.

**Step 4: Run test to verify it passes**
- Run: `cd jaguarclaw-website && npm test`
- Expected: PASS for smoke import/render test.

**Step 5: Commit**
```bash
git add jaguarclaw-website/package.json jaguarclaw-website/tsconfig.json jaguarclaw-website/next.config.mjs jaguarclaw-website/contentlayer.config.ts jaguarclaw-website/next-env.d.ts jaguarclaw-website/.gitignore jaguarclaw-website/README.md
git commit -m "feat(website): bootstrap nextjs contentlayer project"
```

### Task 2: Build design system and global layout shell

**Files:**
- Create: `jaguarclaw-website/app/layout.tsx`
- Create: `jaguarclaw-website/app/globals.css`
- Create: `jaguarclaw-website/components/site-header.tsx`
- Create: `jaguarclaw-website/components/site-footer.tsx`

**Step 1: Write the failing test**
- Test that header links (`/docs`, `/blog`, `/feedback`) and footer metadata render.

**Step 2: Run test to verify it fails**
- Run: `cd jaguarclaw-website && npm test -- site-layout.test.tsx`
- Expected: FAIL due missing components.

**Step 3: Write minimal implementation**
- Implement shared layout with top nav, footer, and industrial design tokens.

**Step 4: Run test to verify it passes**
- Run: `cd jaguarclaw-website && npm test -- site-layout.test.tsx`
- Expected: PASS.

**Step 5: Commit**
```bash
git add jaguarclaw-website/app/layout.tsx jaguarclaw-website/app/globals.css jaguarclaw-website/components/site-header.tsx jaguarclaw-website/components/site-footer.tsx
git commit -m "feat(website): add global shell and visual system"
```

### Task 3: Implement homepage sections

**Files:**
- Create: `jaguarclaw-website/app/page.tsx`
- Create: `jaguarclaw-website/components/hero.tsx`
- Create: `jaguarclaw-website/components/feature-grid.tsx`

**Step 1: Write the failing test**
- Assert homepage renders hero headline, dual CTA buttons, and feature cards.

**Step 2: Run test to verify it fails**
- Run: `cd jaguarclaw-website && npm test -- homepage.test.tsx`
- Expected: FAIL.

**Step 3: Write minimal implementation**
- Create homepage with enterprise positioning, architecture highlights, and quick-start block.

**Step 4: Run test to verify it passes**
- Run: `cd jaguarclaw-website && npm test -- homepage.test.tsx`
- Expected: PASS.

**Step 5: Commit**
```bash
git add jaguarclaw-website/app/page.tsx jaguarclaw-website/components/hero.tsx jaguarclaw-website/components/feature-grid.tsx
git commit -m "feat(website): add enterprise homepage sections"
```

### Task 4: Wire Contentlayer models and seed MDX content

**Files:**
- Create: `jaguarclaw-website/content/docs/getting-started.mdx`
- Create: `jaguarclaw-website/content/docs/architecture-overview.mdx`
- Create: `jaguarclaw-website/content/blog/design-principles.mdx`
- Create: `jaguarclaw-website/content/blog/runtime-evolution.mdx`
- Modify: `jaguarclaw-website/contentlayer.config.ts`

**Step 1: Write the failing test**
- Test content query returns docs and blog records with required frontmatter.

**Step 2: Run test to verify it fails**
- Run: `cd jaguarclaw-website && npm test -- content-model.test.ts`
- Expected: FAIL because content files/models not ready.

**Step 3: Write minimal implementation**
- Define Doc/Post schema and create starter MDX files.

**Step 4: Run test to verify it passes**
- Run: `cd jaguarclaw-website && npm test -- content-model.test.ts`
- Expected: PASS.

**Step 5: Commit**
```bash
git add jaguarclaw-website/contentlayer.config.ts jaguarclaw-website/content/docs jaguarclaw-website/content/blog
git commit -m "feat(website): add content schemas and seed mdx content"
```

### Task 5: Implement docs and blog routes

**Files:**
- Create: `jaguarclaw-website/app/docs/page.tsx`
- Create: `jaguarclaw-website/app/docs/[[...slug]]/page.tsx`
- Create: `jaguarclaw-website/app/blog/page.tsx`
- Create: `jaguarclaw-website/app/blog/[slug]/page.tsx`
- Create: `jaguarclaw-website/lib/content.ts`

**Step 1: Write the failing test**
- Assert doc/blog list page renders seeded content and unknown slug returns 404.

**Step 2: Run test to verify it fails**
- Run: `cd jaguarclaw-website && npm test -- routing-content.test.tsx`
- Expected: FAIL.

**Step 3: Write minimal implementation**
- Build list/detail routes and reusable content query helpers.

**Step 4: Run test to verify it passes**
- Run: `cd jaguarclaw-website && npm test -- routing-content.test.tsx`
- Expected: PASS.

**Step 5: Commit**
```bash
git add jaguarclaw-website/app/docs jaguarclaw-website/app/blog jaguarclaw-website/lib/content.ts
git commit -m "feat(website): add docs and blog routing"
```

### Task 6: Add feedback page and basic SEO metadata

**Files:**
- Create: `jaguarclaw-website/app/feedback/page.tsx`
- Create: `jaguarclaw-website/app/not-found.tsx`
- Create: `jaguarclaw-website/app/sitemap.ts`
- Create: `jaguarclaw-website/app/rss.xml/route.ts`

**Step 1: Write the failing test**
- Assert feedback page renders GitHub Issues CTA and SEO endpoints are reachable.

**Step 2: Run test to verify it fails**
- Run: `cd jaguarclaw-website && npm test -- feedback-seo.test.tsx`
- Expected: FAIL.

**Step 3: Write minimal implementation**
- Implement feedback entry page and lightweight sitemap/rss generation.

**Step 4: Run test to verify it passes**
- Run: `cd jaguarclaw-website && npm test -- feedback-seo.test.tsx`
- Expected: PASS.

**Step 5: Commit**
```bash
git add jaguarclaw-website/app/feedback/page.tsx jaguarclaw-website/app/not-found.tsx jaguarclaw-website/app/sitemap.ts jaguarclaw-website/app/rss.xml/route.ts
git commit -m "feat(website): add feedback and seo endpoints"
```

### Task 7: Verification and migration note

**Files:**
- Modify: `docs/plans/2026-03-05-website-design.md`
- Modify: `jaguarclaw-website/README.md`

**Step 1: Run quality checks**
- Run: `cd jaguarclaw-website && npm run type-check`
- Run: `cd jaguarclaw-website && npm run lint`
- Run: `cd jaguarclaw-website && npm run build`
- Expected: all PASS.

**Step 2: Update migration note**
- Document how to move `jaguarclaw-website` to outer directory as standalone repo/project.

**Step 3: Commit**
```bash
git add docs/plans/2026-03-05-website-design.md jaguarclaw-website/README.md
git commit -m "docs(website): add verification and migration guidance"
```

