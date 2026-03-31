{
  description = "mini-bank dev environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs, ... }:
    let
      pkgs = nixpkgs.legacyPackages.x86_64-linux;
    in {
      devShells.x86_64-linux.default = pkgs.mkShell {
        packages = with pkgs; [
          jdk25
          maven
        ];

        shellHook = ''
          echo "☕ mini-bank dev environment ready"
          echo "Java: $(java -version 2>&1 | head -1)"
          echo "Maven: $(mvn -version 2>&1 | head -1)"
        '';
      };
    };
}
