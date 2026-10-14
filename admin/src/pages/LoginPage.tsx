import { useLogin } from "@refinedev/core";
import { LogIn } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

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

  return (
    <main className="flex min-h-screen items-center justify-center p-4">
      <form onSubmit={submit} className="w-full max-w-sm space-y-4 rounded-lg border border-border bg-card p-6">
        <h1 className="text-xl font-semibold">Saynata Admin</h1>
        <label className="block space-y-1 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Email</span>
          <Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="block space-y-1 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Password</span>
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
        <Button type="submit" size="icon" className="w-full" aria-label="Sign in" title="Sign in" disabled={isPending}>
          <LogIn className="h-4 w-4" />
        </Button>
      </form>
    </main>
  );
}
