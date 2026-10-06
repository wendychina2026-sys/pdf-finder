    private void showOptions(Pdf p) {
        new AlertDialog.Builder(activity)
                .setTitle(p.name)
                .setItems(new String[]{"Mark", "Delete", "Select multiple"}, (d, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(activity, MarkActivity.class);
                        i.putExtra("path", p.path);
                        activity.startActivity(i);
                    } else if (which == 1) {
                        List<String> one = new ArrayList<>();
                        one.add(p.path);
                        confirmDelete(one);
                    } else {
                        adapter.selectionMode = true;
                        adapter.selected.add(p.path);
                        bar.setVisibility(View.VISIBLE);
                        refresh();
                    }
                })
                .show();
    }
