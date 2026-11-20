import { CustomerProvider } from "./CustomerContext.jsx";
import CustomerLayout from "./CustomerLayout.jsx";

export default function CustomerApp({ onNavigateHome }) {
  return (
    <CustomerProvider>
      <CustomerLayout onNavigateHome={onNavigateHome} />
    </CustomerProvider>
  );
}
