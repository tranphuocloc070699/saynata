import { useLogin } from "@refinedev/core";
import { LogIn } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Logo } from "@/components/Layout";

export function LoginPage() {
  const { mutate: login, isPending } = useLogin<{ email: string; password: string }>();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState<string>();

  function submit(event: FormEvent) {
    event.preventDefault();
    setMessage(undefined);
    // A wrong password is not an exception: Refine gives back success: false with our message.
    login(
      { email, password },
      {
        onSuccess: (result) => {
          if (!result.success) setMessage(result.error?.message ?? "Login failed.");
        },
        onError: () => setMessage("Could not reach the server."),
      },
    );
  }

  // Isomorphic split sign-in page (TASK-99): the form on the left, a brand panel on the right.
  return (
    <main className="grid min-h-screen gap-6 bg-card p-6 lg:grid-cols-2">
      <form onSubmit={submit} className="mx-auto flex w-full max-w-sm flex-col justify-center gap-5 py-10">
        <Logo />
        <div className="space-y-2">
          <h1 className="text-[28px] font-semibold leading-tight">Saynata Admin</h1>
          <p className="text-sm text-muted-foreground">Welcome back. Sign in to check the words. Only admin accounts can open this page.</p>
        </div>
        <label className="block space-y-1.5 text-sm">
          <span className="font-medium">Email</span>
          <Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="block space-y-1.5 text-sm">
          <span className="font-medium">Password</span>
          <Input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {message && (
          <p role="alert" className="text-sm text-destructive">
            {message}
          </p>
        )}
        <Button type="submit" size="icon" className="h-11 w-full" aria-label="Sign in" title="Sign in" disabled={isPending}>
          <LogIn className="h-4 w-4" />
        </Button>
      </form>
      <aside className="hidden flex-col items-center justify-center gap-4 rounded-2xl bg-accent p-10 text-center lg:flex">
        <span aria-hidden className="grid h-32 w-44 place-items-center rounded-2xl bg-card font-heading text-5xl text-primary">
          Aa
        </span>
        <h2 className="text-2xl font-semibold">Simple English, checked by you.</h2>
        <p className="max-w-sm text-sm text-muted-foreground">
          Review what Claude wrote, fix the pictures, and keep the dictionary clean.
        </p>
      </aside>
    </main>
  );
}
